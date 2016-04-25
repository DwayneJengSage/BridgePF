package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestUtils.assertResult;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserProfile;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.StudyService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import play.mvc.Result;
import play.test.Helpers;

@RunWith(MockitoJUnitRunner.class)
public class ParticipantControllerTest {
    
    private static final Set<Roles> CALLER_ROLES = Sets.newHashSet(Roles.RESEARCHER);
    
    private static final String ID = "ASDF";

    private static final String EMAIL = "email@email.com";

    private static final TypeReference<PagedResourceList<AccountSummary>> ACCOUNT_SUMMARY_PAGE = new TypeReference<PagedResourceList<AccountSummary>>(){};

    private static final AccountSummary SUMMARY = new AccountSummary("firstName","lastName","email","id",DateTime.now(),AccountStatus.ENABLED);
    
    private static final Study STUDY = new DynamoStudy();
    
    @Spy
    private ParticipantController controller;
    
    @Mock
    private ParticipantService participantService;
    
    @Mock
    private StudyService studyService;
    
    @Captor
    private ArgumentCaptor<Map<ParticipantOption,String>> optionMapCaptor;

    @Captor
    private ArgumentCaptor<UserProfile> profileCaptor;
    
    @Captor
    private ArgumentCaptor<StudyParticipant> participantCaptor;
    
    @Before
    public void before() throws Exception {
        User user = new User();
        user.setRoles(CALLER_ROLES);
        
        UserSession session = new UserSession();
        session.setStudyIdentifier(TestConstants.TEST_STUDY);
        session.setUser(user);
        
        doReturn(session).when(controller).getAuthenticatedSession(Roles.RESEARCHER);
        when(studyService.getStudy(TestConstants.TEST_STUDY)).thenReturn(STUDY);
        
        List<AccountSummary> summaries = Lists.newArrayListWithCapacity(3);
        summaries.add(SUMMARY);
        summaries.add(SUMMARY);
        summaries.add(SUMMARY);
        
        PagedResourceList<AccountSummary> page = new PagedResourceList<AccountSummary>(summaries, 10, 20, 30).withFilter("emailFilter", "foo");
        
        when(participantService.getPagedAccountSummaries(eq(STUDY), anyInt(), anyInt(), any())).thenReturn(page);
        
        controller.setParticipantService(participantService);
        controller.setStudyService(studyService);
        
        TestUtils.mockPlayContext();
    }
    
    @Test
    public void getParticipants() throws Exception {
        Result result = controller.getParticipants("10", "20", "foo");
        PagedResourceList<AccountSummary> page = resultToPage(result);
        
        // verify the result contains items
        assertEquals(3, page.getItems().size());
        assertEquals(30, page.getTotal());
        assertEquals(SUMMARY, page.getItems().get(0));
        
        //verify paging/filtering
        assertEquals(new Integer(10), page.getOffsetBy());
        assertEquals(20, page.getPageSize());
        assertEquals("foo", page.getFilters().get("emailFilter"));
        verify(participantService).getPagedAccountSummaries(STUDY, 10, 20, "foo");
    }
    
    @Test(expected = BadRequestException.class)
    public void oddParametersUseDefaults() throws Exception {
        controller.getParticipants("asdf", "qwer", null);
        
        // paging with defaults
        verify(participantService).getPagedAccountSummaries(STUDY, 0, BridgeConstants.API_DEFAULT_PAGE_SIZE, null);
    }

    @Test
    public void getParticipant() throws Exception {
        StudyParticipant studyParticipant = new StudyParticipant.Builder().withFirstName("Test").build();
        
        when(participantService.getParticipant(STUDY, ID)).thenReturn(studyParticipant);
        
        Result result = controller.getParticipant(ID);
        String string = Helpers.contentAsString(result);
        StudyParticipant retrievedParticipant = BridgeObjectMapper.get().readValue(string, StudyParticipant.class);
        // Verify that there's a field, full serialization tested in StudyParticipant2Test
        assertEquals("Test", retrievedParticipant.getFirstName());
        
        verify(participantService).getParticipant(STUDY, ID);
    }
    
    @Test
    public void signUserOut() throws Exception {
        controller.signOut(ID);
        
        verify(participantService).signUserOut(STUDY, ID);
    }

    @Test
    public void updateParticipant() throws Exception {
        STUDY.getUserProfileAttributes().add("phone");
        TestUtils.mockPlayContextWithJson(TestUtils.createJson("{'firstName':'firstName','lastName':'lastName',"+
                "'email':'email@email.com','externalId':'externalId','password':'newUserPassword',"+
                "'sharingScope':'sponsors_and_partners','notifyByEmail':true,'dataGroups':['group2','group1'],"+
                "'attributes':{'phone':'123456789'},'languages':['en','fr']}"));
        
        Result result = controller.updateParticipant(ID);
        
        assertResult(result, 200, "Participant updated.");
        
        verify(participantService).updateParticipant(eq(STUDY), eq(ID), eq(CALLER_ROLES), participantCaptor.capture());
        
        StudyParticipant participant = participantCaptor.getValue();
        assertEquals("firstName", participant.getFirstName());
        assertEquals("lastName", participant.getLastName());
        assertEquals(EMAIL, participant.getEmail());
        assertEquals("newUserPassword", participant.getPassword());
        assertEquals("externalId", participant.getExternalId());
        assertEquals(SharingScope.SPONSORS_AND_PARTNERS, participant.getSharingScope());
        assertTrue(participant.isNotifyByEmail());
        assertEquals(Sets.newHashSet("group2","group1"), participant.getDataGroups());
        assertEquals("123456789", participant.getAttributes().get("phone"));
        assertEquals(Sets.newHashSet("en","fr"), participant.getLanguages());
    }
    
    @Test
    public void getParticipant2() throws Exception {
        StudyParticipant studyParticipant = new StudyParticipant.Builder().withFirstName("Test").build();
        
        when(participantService.getParticipant(STUDY, EMAIL)).thenReturn(studyParticipant);
        
        Result result = controller.getParticipant2(EMAIL);
        String string = Helpers.contentAsString(result);
        StudyParticipant retrievedParticipant = BridgeObjectMapper.get().readValue(string, StudyParticipant.class);
        // Verify that there's a field, full serialization tested in StudyParticipant2Test
        assertEquals("Test", retrievedParticipant.getFirstName());
        
        verify(participantService).getParticipant(STUDY, EMAIL);
    }
    
    @Test
    public void nullParametersUseDefaults() throws Exception {
        controller.getParticipants(null, null, null);

        // paging with defaults
        verify(participantService).getPagedAccountSummaries(STUDY, 0, BridgeConstants.API_DEFAULT_PAGE_SIZE, null);
    }
    
    @Test
    public void signUserOut2() throws Exception {
        controller.signOut2(EMAIL);
        
        verify(participantService).signUserOut(STUDY, EMAIL);
    }

    @Test(expected = BadRequestException.class)
    public void getParticipantNoEmail2() {
        controller.getParticipant2(null);
    }
    
    @Test(expected = BadRequestException.class)
    public void signOutNoEmail2() throws Exception {
        controller.signOut2(null);
    }

    @Test(expected = BadRequestException.class)
    public void getParticipantBlank2() {
        controller.getParticipant2("");
    }
    
    @Test(expected = BadRequestException.class)
    public void signOutBlank2() throws Exception {
        controller.signOut2("");
    }
    
    @Test
    public void createParticipant() throws Exception {
        IdentifierHolder holder = new IdentifierHolder("ABCD");
        doReturn(holder).when(participantService).createParticipant(eq(STUDY), any(), any(StudyParticipant.class));
        
        STUDY.getUserProfileAttributes().add("phone");
        TestUtils.mockPlayContextWithJson(TestUtils.createJson("{'firstName':'firstName','lastName':'lastName',"+
                "'email':'email@email.com','externalId':'externalId','password':'newUserPassword',"+
                "'sharingScope':'sponsors_and_partners','notifyByEmail':true,'dataGroups':['group2','group1'],"+
                "'attributes':{'phone':'123456789'},'languages':['en','fr']}"));
        
        Result result = controller.createParticipant();
        
        assertEquals(201, result.status());
        String id = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result)).get("identifier").asText();
        assertEquals(holder.getIdentifier(), id);
        
        verify(participantService).createParticipant(eq(STUDY), eq(CALLER_ROLES), participantCaptor.capture());
        
        StudyParticipant participant = participantCaptor.getValue();
        assertEquals("firstName", participant.getFirstName());
        assertEquals("lastName", participant.getLastName());
        assertEquals(EMAIL, participant.getEmail());
        assertEquals("newUserPassword", participant.getPassword());
        assertEquals("externalId", participant.getExternalId());
        assertEquals(SharingScope.SPONSORS_AND_PARTNERS, participant.getSharingScope());
        assertTrue(participant.isNotifyByEmail());
        assertEquals(Sets.newHashSet("group2","group1"), participant.getDataGroups());
        assertEquals("123456789", participant.getAttributes().get("phone"));
        assertEquals(Sets.newHashSet("en","fr"), participant.getLanguages());
    }

    @Test
    public void updateParticipant2() throws Exception {
        STUDY.getUserProfileAttributes().add("phone");
        TestUtils.mockPlayContextWithJson(TestUtils.createJson("{'firstName':'firstName','lastName':'lastName',"+
                "'email':'email@email.com','externalId':'externalId','password':'newUserPassword',"+
                "'sharingScope':'sponsors_and_partners','notifyByEmail':true,'dataGroups':['group2','group1'],"+
                "'attributes':{'phone':'123456789'},'languages':['en','fr']}"));
        
        Result result = controller.updateParticipant2(ID);
        
        assertResult(result, 200, "Participant updated.");
        
        verify(participantService).updateParticipant(eq(STUDY), eq(ID), eq(CALLER_ROLES), participantCaptor.capture());
        
        StudyParticipant participant = participantCaptor.getValue();
        assertEquals("firstName", participant.getFirstName());
        assertEquals("lastName", participant.getLastName());
        assertEquals(EMAIL, participant.getEmail());
        assertEquals("newUserPassword", participant.getPassword());
        assertEquals("externalId", participant.getExternalId());
        assertEquals(SharingScope.SPONSORS_AND_PARTNERS, participant.getSharingScope());
        assertTrue(participant.isNotifyByEmail());
        assertEquals(Sets.newHashSet("group2","group1"), participant.getDataGroups());
        assertEquals("123456789", participant.getAttributes().get("phone"));
        assertEquals(Sets.newHashSet("en","fr"), participant.getLanguages());
    }
    
    @Test(expected = BadRequestException.class)
    public void updateParticipantMissing2() {
        controller.updateParticipant2(null);
    }
    
    @Test(expected = BadRequestException.class)
    public void updateParticipantBlank2() {
        controller.updateParticipant2("");
    }
    
    @Test(expected = BadRequestException.class)
    public void updateParticipantRequiresIdMatch() throws Exception {
        TestUtils.mockPlayContextWithJson(TestUtils.createJson("{'id':'id2'}"));
        
        controller.updateParticipant("id1");
    }

    @Test
    public void updateParticipantNoJsonEmailOK2() throws Exception {
        TestUtils.mockPlayContextWithJson(TestUtils.createJson("{}"));
        
        controller.updateParticipant2(ID);
        verify(participantService).updateParticipant(eq(STUDY), eq(ID), eq(CALLER_ROLES), any());
    }
    
    private PagedResourceList<AccountSummary> resultToPage(Result result) throws Exception {
        String string = Helpers.contentAsString(result);
        return BridgeObjectMapper.get().readValue(string, ACCOUNT_SUMMARY_PAGE);
    }
}
