package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import org.joda.time.DateTimeUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.SmsMessageDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.sms.SmsMessage;
import org.sagebionetworks.bridge.models.sms.SmsType;
import org.sagebionetworks.bridge.models.studies.SmsTemplate;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.sms.SmsMessageProvider;
import org.sagebionetworks.bridge.time.DateUtils;

public class SmsServiceTest {
    private static final String MESSAGE_BODY = "lorem ipsum";
    private static final String MESSAGE_ID = "my-message-id";
    private static final long MOCK_NOW_MILLIS = DateUtils.convertToMillisFromEpoch("2018-10-17T16:21:52.749Z");
    private static final String PHONE_NUMBER = "+12065550123";
    private static final long SENT_ON = 1539732997760L;
    private static final String STUDY_SHORT_NAME = "My Study";
    private static final String USER_ID = "test-user";

    private static final Phone PHONE = new Phone(PHONE_NUMBER, "US");
    private static final StudyParticipant PARTICIPANT_WITH_NO_PHONE = new StudyParticipant.Builder().withId(USER_ID)
            .build();
    private static final StudyParticipant PARTICIPANT_WITH_PHONE = new StudyParticipant.Builder().withId(USER_ID)
            .withPhone(PHONE).build();

    private SmsMessageDao mockMessageDao;
    private ParticipantService mockParticipantService;
    private AmazonSNSClient mockSnsClient;
    private Study study;
    private SmsService svc;

    @BeforeClass
    public static void mockNow() {
        DateTimeUtils.setCurrentMillisFixed(MOCK_NOW_MILLIS);
    }

    @Before
    public void before() {
        // Mock SMS providers.
        mockSnsClient = mock(AmazonSNSClient.class);
        when(mockSnsClient.publish(any())).thenReturn(new PublishResult().withMessageId(MESSAGE_ID));

        // Mock study service. This is only used to get the study short name.
        study = Study.create();
        study.setIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        study.setShortName(STUDY_SHORT_NAME);

        StudyService mockStudyService = mock(StudyService.class);
        when(mockStudyService.getStudy(TestConstants.TEST_STUDY)).thenReturn(study);
        when(mockStudyService.getStudy(TestConstants.TEST_STUDY_IDENTIFIER)).thenReturn(study);

        // Mock other DAOs and services.
        mockMessageDao = mock(SmsMessageDao.class);
        mockParticipantService = mock(ParticipantService.class);

        // Set up service.
        svc = new SmsService();
        svc.setMessageDao(mockMessageDao);
        svc.setParticipantService(mockParticipantService);
        svc.setSnsClient(mockSnsClient);
    }

    @AfterClass
    public static void unmockNow() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void sendTransactionalSMSMessageOK() {
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(study)
                .withSmsTemplate(new SmsTemplate(MESSAGE_BODY))
                .withTransactionType()
                .withPhone(TestConstants.PHONE).build();

        svc.sendSmsMessage(provider);

        ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(mockSnsClient).publish(requestCaptor.capture());

        PublishRequest request = requestCaptor.getValue();
        assertEquals(TestConstants.PHONE.getNumber(), request.getPhoneNumber());
        assertEquals(MESSAGE_BODY, request.getMessage());
        assertEquals("Transactional",
                request.getMessageAttributes().get(BridgeConstants.AWS_SMS_TYPE).getStringValue());
        assertEquals(STUDY_SHORT_NAME,
                request.getMessageAttributes().get(BridgeConstants.AWS_SMS_SENDER_ID).getStringValue());

        // We log the SMS message.
        verifyLoggedSmsMessage(MESSAGE_BODY, SmsType.TRANSACTIONAL);
    }

    @Test
    public void sendPromotionalSMSMessageOK() {
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(study)
                .withSmsTemplate(new SmsTemplate(MESSAGE_BODY))
                .withPromotionType()
                .withPhone(TestConstants.PHONE).build();

        svc.sendSmsMessage(provider);

        ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(mockSnsClient).publish(requestCaptor.capture());

        PublishRequest request = requestCaptor.getValue();
        assertEquals(TestConstants.PHONE.getNumber(), request.getPhoneNumber());
        assertEquals(MESSAGE_BODY, request.getMessage());
        assertEquals("Promotional",
                request.getMessageAttributes().get(BridgeConstants.AWS_SMS_TYPE).getStringValue());
        assertEquals(STUDY_SHORT_NAME,
                request.getMessageAttributes().get(BridgeConstants.AWS_SMS_SENDER_ID).getStringValue());

        // We log the SMS message.
        verifyLoggedSmsMessage(MESSAGE_BODY, SmsType.PROMOTIONAL);
    }

    @Test(expected = BridgeServiceException.class)
    public void sendSMSMessageTooLongInvalid() {
        String message = "This is my SMS message.";
        for (int i=0; i < 5; i++) {
            message += message;
        }
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(study)
                .withSmsTemplate(new SmsTemplate(message))
                .withTransactionType()
                .withPhone(TestConstants.PHONE).build();

        svc.sendSmsMessage(provider);
    }

    private void verifyLoggedSmsMessage(String expectedMessage, SmsType expectedSmsType) {
        ArgumentCaptor<SmsMessage> loggedMessageCaptor = ArgumentCaptor.forClass(SmsMessage.class);
        verify(mockMessageDao).logMessage(loggedMessageCaptor.capture());

        SmsMessage loggedMessage = loggedMessageCaptor.getValue();
        assertEquals(TestConstants.PHONE.getNumber(), loggedMessage.getPhoneNumber());
        assertEquals(MOCK_NOW_MILLIS, loggedMessage.getSentOn());
        assertEquals(expectedMessage, loggedMessage.getMessageBody());
        assertEquals(MESSAGE_ID, loggedMessage.getMessageId());
        assertEquals(expectedSmsType, loggedMessage.getSmsType());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, loggedMessage.getStudyId());
    }

    @Test(expected = BadRequestException.class)
    public void getMostRecentMessage_NullPhoneNumber() {
        svc.getMostRecentMessage(null);
    }

    @Test(expected = BadRequestException.class)
    public void getMostRecentMessage_EmptyPhoneNumber() {
        svc.getMostRecentMessage("");
    }

    @Test(expected = BadRequestException.class)
    public void getMostRecentMessage_BlankPhoneNumber() {
        svc.getMostRecentMessage("   ");
    }

    @Test
    public void getMostRecentMessage_NormalCase() {
        SmsMessage daoOutput = makeValidSmsMessage();
        when(mockMessageDao.getMostRecentMessage(PHONE_NUMBER)).thenReturn(daoOutput);

        SmsMessage svcOutput = svc.getMostRecentMessage(PHONE_NUMBER);
        assertSame(daoOutput, svcOutput);
    }

    @Test(expected = BadRequestException.class)
    public void getMostRecentMessage_ParticipantWithNoPhone() {
        when(mockParticipantService.getParticipant(study, USER_ID, false)).thenReturn(
                PARTICIPANT_WITH_NO_PHONE);
        svc.getMostRecentMessage(study, USER_ID);
    }

    @Test
    public void getMostRecentMessage_ParticipantWithPhone() {
        // Mock dependencies.
        SmsMessage daoOutput = makeValidSmsMessage();
        when(mockMessageDao.getMostRecentMessage(PHONE_NUMBER)).thenReturn(daoOutput);

        when(mockParticipantService.getParticipant(study, USER_ID, false)).thenReturn(
                PARTICIPANT_WITH_PHONE);

        SmsMessage svcOutput = svc.getMostRecentMessage(study, USER_ID);
        assertSame(daoOutput, svcOutput);
    }

    @Test(expected = BadRequestException.class)
    public void logMessage_NullMessage() {
        svc.logMessage(null);
    }

    @Test(expected = InvalidEntityException.class)
    public void logMessage_InvalidMessage() {
        svc.logMessage(SmsMessage.create());
    }

    @Test
    public void logMessage_NormalCase() {
        // Execute.
        SmsMessage svcInput = makeValidSmsMessage();
        svc.logMessage(svcInput);

        // Verify DAO.
        verify(mockMessageDao).logMessage(same(svcInput));
    }

    private static SmsMessage makeValidSmsMessage() {
        SmsMessage message = SmsMessage.create();
        message.setPhoneNumber(PHONE_NUMBER);
        message.setSentOn(SENT_ON);
        message.setMessageId(MESSAGE_ID);
        message.setMessageBody(MESSAGE_BODY);
        message.setSmsType(SmsType.PROMOTIONAL);
        message.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        return message;
    }
}
