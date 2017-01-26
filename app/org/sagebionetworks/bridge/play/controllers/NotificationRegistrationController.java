package org.sagebionetworks.bridge.play.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import org.sagebionetworks.bridge.models.GuidHolder;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.notifications.SubscriptionRequest;
import org.sagebionetworks.bridge.models.notifications.SubscriptionStatus;
import org.sagebionetworks.bridge.services.NotificationTopicService;
import org.sagebionetworks.bridge.services.NotificationsService;

import play.mvc.Result;

@Controller
public class NotificationRegistrationController extends BaseController {
    
    private NotificationsService notificationsService;
    
    private NotificationTopicService topicService;
    
    @Autowired
    final void setNotificationService(NotificationsService notificationsService) {
        this.notificationsService = notificationsService;
    }
    
    @Autowired
    final void setNotificationTopicService(NotificationTopicService topicService) {
        this.topicService = topicService;
    }

    public Result getAllRegistrations() {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        List<NotificationRegistration> registrations = notificationsService.listRegistrations(session.getHealthCode());
        
        return okResult(registrations);
    }
    
    public Result createRegistration() throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();

        NotificationRegistration registration = parseJson(request(), NotificationRegistration.class);
        registration.setHealthCode(session.getHealthCode());
        
        NotificationRegistration result = notificationsService.createRegistration(session.getStudyIdentifier(),
                registration);
        
        return createdResult(new GuidHolder(result.getGuid()));
    }
    
    public Result updateRegistration(String guid) {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        NotificationRegistration registration = parseJson(request(), NotificationRegistration.class);
        registration.setHealthCode(session.getHealthCode());
        registration.setGuid(guid);
        
        NotificationRegistration result = notificationsService.updateRegistration(session.getStudyIdentifier(),
                registration);
        
        return okResult(new GuidHolder(result.getGuid()));
    }
    
    public Result getRegistration(String guid) {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        NotificationRegistration result = notificationsService.getRegistration(session.getHealthCode(), guid);
        
        return okResult(result);
    }
    
    public Result deleteRegistration(String guid) {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        notificationsService.deleteRegistration(session.getHealthCode(), guid);
        
        return okResult("Push notification registration deleted.");
    }

    public Result getSubscriptionStatuses(String registrationGuid) {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        List<SubscriptionStatus> statuses = topicService.currentSubscriptionStatuses(session.getStudyIdentifier(),
                session.getHealthCode(), registrationGuid);
        return okResult(statuses);
    }
    
    public Result subscribe(String registrationGuid) {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        SubscriptionRequest request = parseJson(request(), SubscriptionRequest.class);
        
        List<SubscriptionStatus> statuses = topicService.subscribe(session.getStudyIdentifier(),
                session.getHealthCode(), registrationGuid, request.getTopicGuids());
        return okResult(statuses);
    }
}
