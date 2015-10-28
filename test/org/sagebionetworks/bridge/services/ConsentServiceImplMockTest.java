package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
import org.sagebionetworks.bridge.models.studies.StudyConsentView;
import org.sagebionetworks.bridge.redis.JedisOps;

import com.google.common.collect.Lists;

public class ConsentServiceImplMockTest {

    private static final long UNIX_TIMESTAMP = DateUtils.getCurrentMillisFromEpoch();
    
    private ConsentServiceImpl consentService;

    private AccountDao accountDao;
    private JedisOps jedisOps;
    private ParticipantOptionsService optionsService;
    private SendMailService sendMailService;
    private StudyConsentService studyConsentService;
    private UserConsentDao userConsentDao;
    private ActivityEventService activityEventService;

    private Study study;
    private User user;
    private ConsentSignature consentSignature;
    private Account account;
    
    @Before
    public void before() {
        accountDao = mock(AccountDao.class);
        jedisOps = mock(JedisOps.class);
        optionsService = mock(ParticipantOptionsService.class);
        sendMailService = mock(SendMailService.class);
        userConsentDao = mock(UserConsentDao.class);
        activityEventService = mock(ActivityEventService.class);
        studyConsentService = mock(StudyConsentService.class);

        consentService = new ConsentServiceImpl();
        consentService.setAccountDao(accountDao);
        consentService.setStringOps(jedisOps);
        consentService.setOptionsService(optionsService);
        consentService.setSendMailService(sendMailService);
        consentService.setUserConsentDao(userConsentDao);
        consentService.setActivityEventService(activityEventService);
        consentService.setStudyConsentService(studyConsentService);
        
        study = TestUtils.getValidStudy(ConsentServiceImplMockTest.class);
        user = new User();
        user.setHealthCode("BBB");
        consentSignature = new ConsentSignature.Builder().withName("Test User").withBirthdate("1990-01-01")
                .withSignedOn(UNIX_TIMESTAMP).build();
        
        account = mock(Account.class);
        when(accountDao.getAccount(any(Study.class), any(String.class))).thenReturn(account);
        
    }
    
    @Test
    public void activityEventFiredOnConsent() {
        StudyConsentView view = mock(StudyConsentView.class);
        when(studyConsentService.getActiveConsent(any(Study.class))).thenReturn(view);
        
        UserConsent consent = mock(UserConsent.class);
        when(userConsentDao.giveConsent(user.getHealthCode(), view.getStudyConsent(), UNIX_TIMESTAMP)).thenReturn(consent);
        
        consentService.consentToResearch(study, user, consentSignature, SharingScope.NO_SHARING, false);
        
        verify(activityEventService).publishEnrollmentEvent(user.getHealthCode(), consent);
    }

    @Test
    public void noActivityEventIfTooYoung() {
        consentSignature = new ConsentSignature.Builder().withName("Test User").withBirthdate("2014-01-01")
                .withSignedOn(UNIX_TIMESTAMP).build();
        study.setMinAgeOfConsent(30); // Test is good until 2044. So there.
        
        try {
            consentService.consentToResearch(study, user, consentSignature, SharingScope.NO_SHARING, false);
            fail("Exception expected.");
        } catch(InvalidEntityException e) {
            verifyNoMoreInteractions(activityEventService);
        }
    }
    
    @Test
    public void noActivityEventIfAlreadyConsented() {
        user.setConsent(true);
        
        try {
            consentService.consentToResearch(study, user, consentSignature, SharingScope.NO_SHARING, false);
            fail("Exception expected.");
        } catch(EntityAlreadyExistsException e) {
            verifyNoMoreInteractions(activityEventService);
        }
    }
    
    @Test
    public void noActivityEventIfDaoFails() {
        StudyConsent consent = mock(StudyConsent.class);
        when(userConsentDao.giveConsent(user.getHealthCode(), consent, UNIX_TIMESTAMP)).thenThrow(new RuntimeException());
        
        try {
            consentService.consentToResearch(study, user, consentSignature, SharingScope.NO_SHARING, false);
            fail("Exception expected.");
        } catch(Throwable e) {
            verifyNoMoreInteractions(activityEventService);
        }
    }
    
    @Test
    public void withdrawCopiesSignatureToHistory() {
        List<ConsentSignature> signatures = Lists.newArrayList();
        when(account.getConsentSignatureHistory()).thenReturn(signatures);
        when(account.getConsentSignature()).thenReturn(consentSignature);
        
        consentService.withdrawConsent(study, user);
        
        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        ArgumentCaptor<ConsentSignature> setterCaptor = ArgumentCaptor.forClass(ConsentSignature.class);
        
        verify(userConsentDao).withdrawConsent(user.getHealthCode(), study);
        verify(accountDao).getAccount(study, user.getEmail());
        verify(accountDao).updateAccount(any(Study.class), captor.capture());
        verify(account).setConsentSignature(setterCaptor.capture());
        verifyNoMoreInteractions(userConsentDao);
        verifyNoMoreInteractions(accountDao);
        
        Account account = captor.getValue();
        // The signature has been moved to the array, and nullified. User object
        // is marked not consented.
        assertEquals(1, account.getConsentSignatureHistory().size());
        assertEquals(consentSignature, account.getConsentSignatureHistory().get(0));
        assertNull(setterCaptor.getValue()); // should be nullified
        assertFalse(user.isConsent());
    }
    
}