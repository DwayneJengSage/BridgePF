package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.annotation.Resource;

import org.apache.commons.io.IOUtils;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.StudyLimitExceededException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
import org.sagebionetworks.bridge.models.studies.StudyConsentView;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.redis.JedisOps;
import org.sagebionetworks.bridge.redis.RedisKey;
import org.sagebionetworks.bridge.services.email.ConsentEmailProvider;
import org.sagebionetworks.bridge.services.email.MimeTypeEmailProvider;
import org.sagebionetworks.bridge.validators.ConsentAgeValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

@Component
public class ConsentServiceImpl implements ConsentService {

    private static final int TWENTY_FOUR_HOURS = (24 * 60 * 60);

    private AccountDao accountDao;
    private JedisOps jedisOps;
    private ParticipantOptionsService optionsService;
    private SendMailService sendMailService;
    private StudyConsentService studyConsentService;
    private UserConsentDao userConsentDao;
    private ActivityEventService activityEventService;
    private String consentTemplate;
    
    @Value("classpath:study-defaults/consent-page.xhtml")
    final void setConsentTemplate(org.springframework.core.io.Resource resource) throws IOException {
        this.consentTemplate = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    
    @Autowired
    public final void setStringOps(JedisOps jedisOps) {
        this.jedisOps = jedisOps;
    }
    @Resource(name="stormpathAccountDao")
    public final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }
    @Autowired
    public final void setOptionsService(ParticipantOptionsService optionsService) {
        this.optionsService = optionsService;
    }
    @Autowired
    public final void setSendMailService(SendMailService sendMailService) {
        this.sendMailService = sendMailService;
    }
    @Autowired
    public final void setStudyConsentService(StudyConsentService studyConsentService) {
        this.studyConsentService = studyConsentService;
    }
    @Autowired
    public final void setUserConsentDao(UserConsentDao userConsentDao) {
        this.userConsentDao = userConsentDao;
    }
    @Autowired
    public final void setActivityEventService(ActivityEventService activityEventService) {
        this.activityEventService = activityEventService;
    }
    
    @Override
    public ConsentSignature getConsentSignature(final Study study, final User user) {
        checkNotNull(user, Validate.CANNOT_BE_NULL, "user");
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        
        Account account = accountDao.getAccount(study, user.getEmail());
        ConsentSignature consentSignature = account.getConsentSignature();
        if (consentSignature != null) {
            return consentSignature;
        }
        throw new EntityNotFoundException(ConsentSignature.class);
    }

    @Override
    public User consentToResearch(final Study study, final User user, final ConsentSignature consentSignature,
            final SharingScope sharingScope, final boolean sendEmail) {

        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        checkNotNull(user, Validate.CANNOT_BE_NULL, "user");
        checkNotNull(consentSignature, Validate.CANNOT_BE_NULL, "consentSignature");
        checkNotNull(sharingScope, Validate.CANNOT_BE_NULL, "sharingScope");

        if (user.doesConsent()) {
            throw new EntityAlreadyExistsException(consentSignature);
        }
        ConsentAgeValidator validator = new ConsentAgeValidator(study);
        Validate.entityThrowingException(validator, consentSignature);

        final StudyConsentView studyConsent = studyConsentService.getActiveConsent(study);
        final Account account = accountDao.getAccount(study, user.getEmail());

        // Throws exception if we have exceeded enrollment limit.
        incrementStudyEnrollment(study);
        
        account.setConsentSignature(consentSignature);
        accountDao.updateAccount(study, account);
        
        UserConsent userConsent = null;
        try {
            userConsent = userConsentDao.giveConsent(user.getHealthCode(), studyConsent.getStudyConsent(),
                    consentSignature.getSignedOn());
        } catch (Throwable e) {
            // If we can't save consent record, decrement and remove the signature before rethrowing
            decrementStudyEnrollment(study);
            account.setConsentSignature(null);
            accountDao.updateAccount(study, account);
            throw e;
        }
        
        // Save supplemental records, fire events, etc.
        if (userConsent != null){
            activityEventService.publishEnrollmentEvent(user.getHealthCode(), userConsent);
        }
        optionsService.setOption(study, user.getHealthCode(), sharingScope);
        if (sendEmail) {
            MimeTypeEmailProvider consentEmail = new ConsentEmailProvider(study, user, 
                consentSignature, sharingScope, studyConsentService, consentTemplate);

            sendMailService.sendEmail(consentEmail);
        }
        user.setConsent(true);
        return user;
    }

    @Override
    public boolean hasUserConsentedToResearch(StudyIdentifier studyIdentifier, User user) {
        checkNotNull(user, Validate.CANNOT_BE_NULL, "user");
        checkNotNull(studyIdentifier, Validate.CANNOT_BE_NULL, "studyIdentifier");

        return userConsentDao.hasConsented(user.getHealthCode(), studyIdentifier);
    }

    @Override
    public boolean hasUserSignedMostRecentConsent(StudyIdentifier studyIdentifier, User user) {
        checkNotNull(user, Validate.CANNOT_BE_NULL, "user");
        checkNotNull(studyIdentifier, Validate.CANNOT_BE_NULL, "studyIdentifier");

        UserConsent userConsent = userConsentDao.getActiveUserConsent(user.getHealthCode(), studyIdentifier);
        StudyConsentView mostRecentConsent = studyConsentService.getActiveConsent(studyIdentifier);

        if (mostRecentConsent != null && userConsent != null) {
            return userConsent.getConsentCreatedOn() == mostRecentConsent.getCreatedOn();
        } else {
            return false;
        }
    }

    @Override
    public void withdrawConsent(Study study, User user) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        checkNotNull(user, Validate.CANNOT_BE_NULL, "user");
        
        userConsentDao.withdrawConsent(user.getHealthCode(), study);
        decrementStudyEnrollment(study);
        Account account = accountDao.getAccount(study, user.getEmail());
        
        ConsentSignature signature = account.getConsentSignature();
        List<ConsentSignature> history = account.getConsentSignatureHistory();
        if (history == null) {
            history = Lists.newArrayListWithCapacity(1);
        }
        history.add(signature);
        account.setConsentSignatureHistory(history);
        account.setConsentSignature(null);
        accountDao.updateAccount(study, account);
        user.setConsent(false);
    }

    // Note: this doesn't include the withdrawal dates, as these are only recorded
    // in DDB, and it doesn't include a pointer to the version of the consent that 
    // was signed. We probably need this for auditing, but not at the moment.
    @Override
    public List<ConsentSignature> getUserConsentHistory(Study study, User user) {
        List<ConsentSignature> history = Lists.newArrayList();
        
        Account account = accountDao.getAccount(study, user.getEmail());
        if (account.getConsentSignatureHistory() != null) {
            history.addAll(account.getConsentSignatureHistory());
        }
        if (account.getConsentSignature() != null) {
            history.add(account.getConsentSignature());
        }
        return history;
    }
    
    @Override
    public void deleteAllConsents(Study study, User user) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        checkNotNull(user, Validate.CANNOT_BE_NULL, "user");

        userConsentDao.deleteAllConsents(user.getHealthCode(), study.getStudyIdentifier());
    }
    
    @Override
    public void emailConsentAgreement(final Study study, final User user) {
        checkNotNull(user, Validate.CANNOT_BE_NULL, "user");
        checkNotNull(study, Validate.CANNOT_BE_NULL, "studyIdentifier");

        final StudyConsentView consent = studyConsentService.getActiveConsent(study);
        if (consent == null) {
            throw new EntityNotFoundException(StudyConsent.class);
        }

        final ConsentSignature consentSignature = getConsentSignature(study, user);
        if (consentSignature == null) {
            throw new EntityNotFoundException(ConsentSignature.class);
        }

        final SharingScope sharingScope = optionsService.getSharingScope(user.getHealthCode());
        MimeTypeEmailProvider consentEmail = new ConsentEmailProvider(
            study, user, consentSignature, sharingScope, studyConsentService, consentTemplate);
        sendMailService.sendEmail(consentEmail);
    }

    @Override
    public boolean isStudyAtEnrollmentLimit(Study study) {
        if (study.getMaxNumOfParticipants() == 0) {
            return false;
        }
        String key = RedisKey.NUM_OF_PARTICIPANTS.getRedisKey(study.getIdentifier());

        long count = Long.MAX_VALUE;
        String countString = jedisOps.get(key);
        if (countString == null) {
            // This is expensive but don't lock, it's better to do it twice slowly, than to throw an exception here.
            count = userConsentDao.getNumberOfParticipants(study.getStudyIdentifier());
            jedisOps.setex(key, TWENTY_FOUR_HOURS, Long.toString(count));
        } else {
            count = Long.parseLong(countString);
        }
        return (count >= study.getMaxNumOfParticipants());
    }

    @Override
    public void incrementStudyEnrollment(Study study) throws StudyLimitExceededException {
        if (study.getMaxNumOfParticipants() == 0) {
            return;
        }
        if (isStudyAtEnrollmentLimit(study)) {
            throw new StudyLimitExceededException(study);
        }
        String key = RedisKey.NUM_OF_PARTICIPANTS.getRedisKey(study.getIdentifier());
        jedisOps.incr(key);
    }

    @Override
    public void decrementStudyEnrollment(Study study) {
        if (study.getMaxNumOfParticipants() == 0) {
            return;
        }
        String key = RedisKey.NUM_OF_PARTICIPANTS.getRedisKey(study.getIdentifier());
        String count = jedisOps.get(key);
        if (count != null && Long.parseLong(count) > 0) {
            jedisOps.decr(key);
        }
    }
}
