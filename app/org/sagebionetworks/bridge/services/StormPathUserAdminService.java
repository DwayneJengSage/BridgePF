package org.sagebionetworks.bridge.services;

import java.util.List;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.dao.UserLockDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.models.ConsentSignature;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.Tracker;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.healthdata.HealthDataKey;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.account.AccountCriteria;
import com.stormpath.sdk.account.AccountList;
import com.stormpath.sdk.account.Accounts;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.Directory;
import com.stormpath.sdk.group.Group;
import com.stormpath.sdk.group.GroupList;

public class StormPathUserAdminService implements UserAdminService {

    private AuthenticationService authenticationService;
    private ConsentService consentService;
    private HealthDataService healthDataService;
    private StudyService studyService;
    private Client stormpathClient;
    private UserLockDao userLockDao;

    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    public void setConsentService(ConsentService consentService) {
        this.consentService = consentService;
    }

    public void setHealthDataService(HealthDataService healthDataService) {
        this.healthDataService = healthDataService;
    }

    public void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }

    public void setStormpathClient(Client stormpathClient) {
        this.stormpathClient = stormpathClient;
    }
    
    public void setUserLockDao(UserLockDao userLockDao) {
        this.userLockDao = userLockDao;
    }

    @Override
    public UserSession createUser(SignUp signUp, List<String> roles, Study userStudy, boolean signUserIn,
            boolean consentUser) throws BridgeServiceException {
        if (signUp == null) {
            throw new BridgeServiceException("User cannot be null", 400);
        } else if (StringUtils.isBlank(signUp.getUsername())) {
            throw new BridgeServiceException("User's username cannot be null", 400);
        } else if (StringUtils.isBlank(signUp.getEmail())) {
            throw new BridgeServiceException("User's email cannot be null", 400);
        } else if (StringUtils.isBlank(signUp.getPassword())) {
            throw new BridgeServiceException("User's password cannot be null", 400);
        } else if (userStudy == null) {
            throw new BridgeServiceException("User study cannot be null", 400);
        }
        try {
            Directory directory = getDirectory(userStudy);
            // Search for email and skip creation if it already exists.
            if (userDoesNotExist(directory, signUp.getEmail())) {
                Account account = stormpathClient.instantiate(Account.class);
                account.setGivenName("<EMPTY>");
                account.setSurname("<EMPTY>");
                account.setEmail(signUp.getEmail());
                account.setUsername(signUp.getUsername());
                account.setPassword(signUp.getPassword());
                directory.createAccount(account, false); // suppress email message
                addAccountToGroups(directory, account, roles);
            }
        } catch (Throwable t) {
            throw new BridgeServiceException(t, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
        SignIn signIn = new SignIn(signUp.getUsername(), signUp.getPassword());
        UserSession newUserSession = null;
        try {
            newUserSession = authenticationService.signIn(userStudy, signIn);
        } catch (ConsentRequiredException e) {
            newUserSession = e.getUserSession();
            if (consentUser) {
                ConsentSignature consent = new ConsentSignature("Test Signature", "1989-08-19");
                consentService.consentToResearch(newUserSession.getUser(), consent, userStudy, false);

                // Now, sign in again so you get the consented user into the session
                authenticationService.signOut(newUserSession.getSessionToken());
                newUserSession = authenticationService.signIn(userStudy, signIn);
            }
        }
        if (!signUserIn) {
            authenticationService.signOut(newUserSession.getSessionToken());
            newUserSession = null;
        }
        return newUserSession;
    }

    @Override
    public void revokeAllConsentRecords(User user, Study userStudy) throws BridgeServiceException {
        if (user == null) {
            throw new BridgeServiceException("User cannot be null", 400);
        } else if (userStudy == null) {
            throw new BridgeServiceException("User study cannot be null", 400);
        }
        consentService.withdrawConsent(user, userStudy);
    }

    @Override
    public void deleteUser(User user) throws BridgeServiceException {
        if (user == null) {
            throw new BridgeServiceException("User cannot be null", 400);
        }
        for (Study study : studyService.getStudies()) {
            deleteUserInStudy(user, study);
        }
    }
    
    private void addAccountToGroups(Directory directory, Account account, List<String> roles) {
        if (roles != null) {
            GroupList groups = directory.getGroups();
            for (Group group : groups) {
                if (roles.contains(group.getName())) {
                    account.addGroup(group);
                }
            }
        }
    }

    private void deleteUserInStudy(User user, Study userStudy) throws BridgeServiceException {
        if (user == null) {
            throw new BridgeServiceException("User cannot be null", 400);
        } else if (userStudy == null) {
            throw new BridgeServiceException("User study cannot be null", 400);
        }
        String uuid = null;
        try {
            uuid = userLockDao.createLock(user.getId());
            
            // Verify the user exists before doing this work. Otherwise, it just throws errors.
            Directory directory = getDirectory(userStudy);
            Account account = getUserAccountByEmail(directory, user.getEmail());
            if (account != null) {
                revokeAllConsentRecords(user, userStudy);
                removeAllHealthDataRecords(user, userStudy);
                deleteUserAccount(userStudy, user.getEmail());
            }
        } catch (Throwable t) {
            throw new BridgeServiceException(t, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        } finally {
            if (uuid != null) {
                userLockDao.releaseLock(user.getId(), uuid);
            }
        }
    }

    private void removeAllHealthDataRecords(User user, Study userStudy) throws BridgeServiceException {
        // This user may have never consented to research. Ignore if that's the case.
        if (user.getHealthDataCode() != null) {
            List<Tracker> trackers = userStudy.getTrackers();
            HealthDataKey key = null;
            for (Tracker tracker : trackers) {
                key = new HealthDataKey(userStudy, tracker, user);
                List<HealthDataRecord> records = healthDataService.getAllHealthData(key);
                for (HealthDataRecord record : records) {
                    healthDataService.deleteHealthDataRecord(key, record.getRecordId());
                }
            }
        }
    }

    private void deleteUserAccount(Study userStudy, String userEmail) throws BridgeServiceException {
        if (StringUtils.isBlank(userEmail)) {
            throw new BridgeServiceException("User email cannot be blank", 400);
        }
        try {
            Directory directory = getDirectory(userStudy);
            Account account = getUserAccountByEmail(directory, userEmail);
            if (account != null) {
                account.delete();
            }
        } catch (Exception e) {
            throw new BridgeServiceException(e, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private Directory getDirectory(Study userStudy) {
        return stormpathClient.getResource(userStudy.getStormpathDirectoryHref(), Directory.class);
    }

    private boolean userDoesNotExist(Directory directory, String email) {
        return (getUserAccountByEmail(directory, email) == null);
    }

    private Account getUserAccountByEmail(Directory directory, String email) {
        AccountCriteria criteria = Accounts.where(Accounts.email().eqIgnoreCase(email));
        AccountList accounts = directory.getAccounts(criteria);
        return (accounts.iterator().hasNext()) ? accounts.iterator().next() : null;
    }
}
