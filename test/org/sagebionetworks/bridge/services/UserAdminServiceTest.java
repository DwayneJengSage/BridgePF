package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.TestConstants.TEST_CONTEXT;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import java.util.Optional;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dynamodb.DynamoExternalIdentifier;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;
import org.sagebionetworks.bridge.models.substudies.Substudy;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;

import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class UserAdminServiceTest {

    // Decided not to use the helper class for this test because so many edge conditions are
    // being tested here.

    @Resource
    AuthenticationService authService;

    @Resource
    BridgeConfig bridgeConfig;

    @Resource
    StudyService studyService;

    @Resource
    UserAdminService userAdminService;
    
    @Resource
    ExternalIdService externalIdService;
    
    @Resource
    SubstudyService substudyService;
    
    @Resource(name = "externalIdDdbMapper")
    private DynamoDBMapper mapper;
    
    private Study study;
    
    private Substudy substudy;

    private StudyParticipant participant;

    private UserSession session;

    @Before
    public void before() {
        study = studyService.getStudy(TEST_STUDY_IDENTIFIER);
        String email = TestUtils.makeRandomTestEmail(UserAdminServiceTest.class);
        participant = new StudyParticipant.Builder().withEmail(email).withPassword("P4ssword!").build();
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerStudyId(TestConstants.TEST_STUDY).build());
        
        substudy = Substudy.create();
        substudy.setName("Test Substudy");
        substudy.setId(TestUtils.randomName(UserAdminServiceTest.class));
        substudyService.createSubstudy(TestConstants.TEST_STUDY, substudy);
    }

    @After
    public void after() {
        if (session != null) {
            userAdminService.deleteUser(study, session.getId());
        }
        substudyService.deleteSubstudyPermanently(TestConstants.TEST_STUDY, substudy.getId());
        BridgeUtils.setRequestContext(null);
    }

    @Test
    public void deletedUserHasBeenDeleted() {
        session = userAdminService.createUser(study, participant, null, true, true);

        userAdminService.deleteUser(study, session.getId());
        session = null;

        // This should fail with a 404.
        try {
            authService.signIn(study, TEST_CONTEXT, new SignIn.Builder().withStudy(study.getIdentifier())
                    .withEmail(participant.getEmail()).withPassword(participant.getPassword()).build());
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
            
        }
    }

    @Test
    public void canCreateConsentedAndSignedInUser() {
        session = userAdminService.createUser(study, participant, null, true, true);
        
        assertTrue(session.isAuthenticated());
        assertTrue(session.doesConsent());
        for(ConsentStatus status : session.getConsentStatuses().values()) {
            assertTrue(status.isConsented());
        }
    }
    
    @Test
    public void canCreateUserWithoutConsentingOrSigningUserIn() {
        session = userAdminService.createUser(study, participant, null, false, false);
        assertFalse(session.isAuthenticated());
        
        try {
            session = authService.signIn(study, TEST_CONTEXT, new SignIn.Builder().withStudy(study.getIdentifier())
                    .withEmail(participant.getEmail()).withPassword(participant.getPassword()).build());
            fail("Should have thrown exception");
        } catch (ConsentRequiredException e) {
            assertFalse(e.getUserSession().doesConsent());    
        }
    }

    @Test
    public void cannotCreateUserWithSameEmail() {
        
        session = userAdminService.createUser(study, participant, null, true, false);
        try {
            userAdminService.createUser(study, participant, null, false, false);
            fail("Sign up with email already in use should throw an exception");
        } catch(EntityAlreadyExistsException e) { 
            assertEquals("Email address has already been used by another account.", e.getMessage());
        }
    }

    @Test
    public void testDeleteUserWhenSignedOut() {
        session = userAdminService.createUser(study, participant, null, true, true);
        authService.signOut(session);
        assertNull(authService.getSession(session.getSessionToken()));
        // Shouldn't crash
        userAdminService.deleteUser(study, session.getId());
        assertNull(authService.getSession(session.getSessionToken()));
        session = null;
    }

    @Test
    public void testDeleteUserThatHasBeenDeleted() {
        session = userAdminService.createUser(study, participant, null, true, true);
        userAdminService.deleteUser(study, session.getId());
        assertNull(authService.getSession(session.getSessionToken()));
        // Delete again shouldn't crash
        userAdminService.deleteUser(study, session.getId());
        assertNull(authService.getSession(session.getSessionToken()));
        session = null;
    }
    
    @Test
    public void creatingUserThenDeletingRemovesExternalIdAssignment() throws Exception {
        String externalId = BridgeUtils.generateGuid();
        participant = new StudyParticipant.Builder().copyOf(participant).withExternalId(externalId).build();
        
        ExternalIdentifier idForTest = ExternalIdentifier.create(study.getStudyIdentifier(), externalId);
        idForTest.setSubstudyId(substudy.getId());
        externalIdService.createExternalId(idForTest, false);
        try {
            study.setExternalIdValidationEnabled(true);
            session = userAdminService.createUser(study, participant, null, true, true);

            DynamoExternalIdentifier identifier = getDynamoExternalIdentifier(session, externalId);
            assertEquals(session.getHealthCode(), identifier.getHealthCode());
            
            // Now delete the user, and the assignment should then be free;
            userAdminService.deleteUser(study, session.getId());
            
            identifier = getDynamoExternalIdentifier(session, externalId);
            assertNull(identifier.getHealthCode());
            
            // Now this works
            Account account = Account.create();
            account.setId(BridgeUtils.generateGuid());
            account.setStudyId(session.getStudyIdentifier().getIdentifier());
            account.setHealthCode(BridgeUtils.generateGuid());
            ExternalIdentifier extIdObj = setupExternalId(account, externalId);
            externalIdService.commitAssignExternalId(extIdObj);
        } finally {
            session = null;
            // this is a cheat, for sure, but allow deletion
            study.setExternalIdValidationEnabled(false);
            externalIdService.deleteExternalIdPermanently(study, idForTest);
        }
    }
    
    // This behavior is very similar to ParticipantService.beginAssignExternalId().
    private ExternalIdentifier setupExternalId(Account account, String externalId) {
        Optional<ExternalIdentifier> optionalId = externalIdService.getExternalId(TestConstants.TEST_STUDY, externalId);
        if (!optionalId.isPresent()) {
            return null;
        }
        ExternalIdentifier identifier = optionalId.get();
        identifier.setHealthCode(account.getHealthCode());
        if (account.getExternalId() == null) {
            account.setExternalId(identifier.getIdentifier());    
        }
        if (identifier.getSubstudyId() != null) {
            AccountSubstudy acctSubstudy = AccountSubstudy.create(account.getStudyId(),
                    identifier.getSubstudyId(), account.getId());
            acctSubstudy.setExternalId(identifier.getIdentifier());
            if (!account.getAccountSubstudies().contains(acctSubstudy)) {
                account.getAccountSubstudies().add(acctSubstudy);    
            }
        }
        return identifier;
    }

    private DynamoExternalIdentifier getDynamoExternalIdentifier(UserSession session, String externalId) {
        DynamoExternalIdentifier keyObject = new DynamoExternalIdentifier(study.getIdentifier(), externalId);
        return mapper.load(keyObject);
    }
}
