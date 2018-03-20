package org.sagebionetworks.bridge.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Map;

import org.junit.Test;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.studies.SmsTemplate;
import org.sagebionetworks.bridge.models.studies.Study;

import com.amazonaws.services.sns.model.PublishRequest;

public class SmsMessageProviderTest {
    @Test
    public void test() throws Exception {
        // Set up dependencies
        Study study = Study.create();
        study.setName("Name");
        study.setShortName("ShortName");
        study.setIdentifier("id");
        study.setSponsorName("SponsorName");
        study.setSupportEmail("support@email.com");
        study.setTechnicalEmail("tech@email.com");
        study.setConsentNotificationEmail("consent@email.com,consent2@email.com");

        SmsTemplate template = new SmsTemplate("${studyShortName} ${url} ${supportEmail} ${expirationPeriod}");
        
        // Create
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
            .withStudy(study)
            .withPhone(TestConstants.PHONE)
            .withSmsTemplate(template)
            .withExpirationPeriod("expirationPeriod", 60*60*4) // 4 hours
            .withToken("url", "some-url").build();
        
        // Check email
        PublishRequest request = provider.getSmsRequest();
        assertEquals("ShortName some-url support@email.com 4 hours", request.getMessage());
        assertEquals(study.getShortName(),
                request.getMessageAttributes().get(BridgeConstants.SENDER_ID).getStringValue());
        assertEquals(BridgeConstants.SMS_TYPE_TRANSACTIONAL,
                request.getMessageAttributes().get(BridgeConstants.SMS_TYPE).getStringValue());
        
        assertEquals("some-url", provider.getTokenMap().get("url"));
        assertEquals("4 hours", provider.getTokenMap().get("expirationPeriod"));
        // BridgeUtils.studyTemplateVariables() has been called
        assertEquals("Name", provider.getTokenMap().get("studyName"));
        assertEquals("ShortName", provider.getTokenMap().get("studyShortName"));
        assertEquals("id", provider.getTokenMap().get("studyId"));
        assertEquals("SponsorName", provider.getTokenMap().get("sponsorName"));
        assertEquals("support@email.com", provider.getTokenMap().get("supportEmail"));
        assertEquals("tech@email.com", provider.getTokenMap().get("technicalEmail"));
        assertEquals("consent@email.com", provider.getTokenMap().get("consentEmail"));
    }
    
    @Test
    public void defaultsShortNameToBridge() {
        // Set up dependencies
        Study study = Study.create();
        SmsTemplate template = new SmsTemplate("${studyShortName} ${url} ${supportEmail}");
        
        // Create
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
            .withStudy(study)
            .withPhone(TestConstants.PHONE)
            .withSmsTemplate(template)
            .withToken("url", "some-url").build();
        PublishRequest request = provider.getSmsRequest();
        assertEquals("Bridge some-url", request.getMessage());
    }
    
    @Test
    public void nullTokenMapEntryDoesntBreakMap() {
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(Study.create())
                .withPhone(TestConstants.PHONE)
                .withSmsTemplate(new SmsTemplate(""))
                .withToken("url", null).build();
        
        Map<String,String> tokenMap = provider.getTokenMap();
        assertNull(tokenMap.get("supportName"));
    }

}
