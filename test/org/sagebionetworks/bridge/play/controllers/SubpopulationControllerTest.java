package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestUtils.assertResult;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.SubpopulationService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import play.mvc.Result;
import play.test.Helpers;

@RunWith(MockitoJUnitRunner.class)
public class SubpopulationControllerTest {

    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create("AAA");
    private static final StudyIdentifier STUDY_IDENTIFIER = new StudyIdentifierImpl("test-key");
    private static final TypeReference<ResourceList<Subpopulation>> subpopType = new TypeReference<ResourceList<Subpopulation>>() {};
    
    @Spy
    private SubpopulationController controller;
    
    @Mock
    private SubpopulationService subpopService;
    
    @Mock
    private StudyService studyService;
    
    @Mock
    private Study study;
    
    @Captor
    private ArgumentCaptor<Subpopulation> captor;
    
    private UserSession session;
    
    private StudyParticipant participant;
    
    @Before
    public void before() throws Exception {
        participant = new StudyParticipant.Builder().withRoles(Sets.newHashSet(Roles.DEVELOPER)).build();
        session = new UserSession(participant);
        session.setStudyIdentifier(STUDY_IDENTIFIER);
        session.setAuthenticated(true);
        
        controller.setSubpopulationService(subpopService);
        controller.setStudyService(studyService);
        
        when(study.getStudyIdentifier()).thenReturn(STUDY_IDENTIFIER);
        doReturn(session).when(controller).getSessionIfItExists();
        when(studyService.getStudy(STUDY_IDENTIFIER)).thenReturn(study);
    }
    
    @Test
    public void getAllSubpopulationsExcludeDeleted() throws Exception {
        TestUtils.mockPlay().mock();
        
        List<Subpopulation> list = createSubpopulationList();
        when(subpopService.getSubpopulations(study.getStudyIdentifier(), false)).thenReturn(list);
        
        Result result = controller.getAllSubpopulations("false");
        TestUtils.assertResult(result, 200);

        String json = Helpers.contentAsString(result);
        
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        JsonNode oneSubpop = node.get("items").get(0);
        assertNull(oneSubpop.get("studyIdentifier"));
        
        ResourceList<Subpopulation> rList = BridgeObjectMapper.get().readValue(json, subpopType);
        assertEquals(list, rList.getItems());
        assertEquals(2, rList.getItems().size());
        
        verify(subpopService).getSubpopulations(study.getStudyIdentifier(), false);
    }

    @Test
    public void getAllSubpopulationsIncludeDeleted() throws Exception {
        TestUtils.mockPlay().mock();
        
        List<Subpopulation> list = createSubpopulationList();
        when(subpopService.getSubpopulations(study.getStudyIdentifier(), true)).thenReturn(list);
        
        Result result = controller.getAllSubpopulations("true");
        TestUtils.assertResult(result, 200);
        
        ResourceList<Subpopulation> payload = TestUtils.getResponsePayload(result,
                new TypeReference<ResourceList<Subpopulation>>() {});
        assertEquals(2, payload.getItems().size());
        
        verify(subpopService).getSubpopulations(study.getStudyIdentifier(), true);
    }

    @Test
    public void getAllSubpopulationsDefaultExcludeDeleted() throws Exception {
        TestUtils.mockPlay().mock();
        
        List<Subpopulation> list = createSubpopulationList();
        when(subpopService.getSubpopulations(study.getStudyIdentifier(), false)).thenReturn(list);
        
        Result result = controller.getAllSubpopulations(null);
        TestUtils.assertResult(result, 200);
        
        verify(subpopService).getSubpopulations(study.getStudyIdentifier(), false);
    }
    
    @Test
    public void createSubpopulation() throws Exception {
        String json = TestUtils.createJson("{'guid':'junk','name':'Name','defaultGroup':true,'description':'Description','required':true,'criteria':{'minAppVersion':2,'maxAppVersion':10,'allOfGroups':['requiredGroup'],'noneOfGroups':['prohibitedGroup']}}");
        TestUtils.mockPlay().withJsonBody(json).mock();
        
        Subpopulation createdSubpop = Subpopulation.create();
        createdSubpop.setGuidString("AAA");
        createdSubpop.setVersion(1L);
        doReturn(createdSubpop).when(subpopService).createSubpopulation(eq(study), captor.capture());

        Result result = controller.createSubpopulation();
        TestUtils.assertResult(result, 201);

        String responseJSON = Helpers.contentAsString(result);
        JsonNode node = BridgeObjectMapper.get().readTree(responseJSON);
        assertEquals("AAA", node.get("guid").asText());
        assertEquals(1L, node.get("version").asLong());
        assertEquals("GuidVersionHolder", node.get("type").asText());
        
        Subpopulation created = captor.getValue();
        assertEquals("Name", created.getName());
        assertEquals("Description", created.getDescription());
        assertTrue(created.isDefaultGroup());
        Criteria criteria = created.getCriteria();
        assertEquals((Integer)2, criteria.getMinAppVersion(OperatingSystem.IOS));
        assertEquals((Integer)10, criteria.getMaxAppVersion(OperatingSystem.IOS));
        assertEquals(Sets.newHashSet("requiredGroup"), criteria.getAllOfGroups());
        assertEquals(Sets.newHashSet("prohibitedGroup"), criteria.getNoneOfGroups());
    }
    
    @Test
    public void updateSubpopulation() throws Exception {
        String json = TestUtils.createJson("{'name':'Name','description':'Description','defaultGroup':true,'required':true,'criteria':{'minAppVersion':2,'maxAppVersion':10,'allOfGroups':['requiredGroup'],'noneOfGroups':['prohibitedGroup']}}");
        TestUtils.mockPlay().withJsonBody(json).mock();
        
        Subpopulation createdSubpop = Subpopulation.create();
        createdSubpop.setGuidString("AAA");
        createdSubpop.setVersion(1L);
        doReturn(createdSubpop).when(subpopService).updateSubpopulation(eq(study), captor.capture());

        Result result = controller.updateSubpopulation("AAA");
        TestUtils.assertResult(result, 200);

        String responseJSON = Helpers.contentAsString(result);
        JsonNode node = BridgeObjectMapper.get().readTree(responseJSON);
        assertEquals("AAA", node.get("guid").asText());
        assertEquals(1L, node.get("version").asLong());
        assertEquals("GuidVersionHolder", node.get("type").asText());
        
        Subpopulation created = captor.getValue();
        assertEquals("AAA", created.getGuidString());
        assertEquals("Name", created.getName());
        assertEquals("Description", created.getDescription());
        assertTrue(created.isDefaultGroup());
        Criteria criteria = created.getCriteria();
        assertEquals((Integer)2, criteria.getMinAppVersion(OperatingSystem.IOS));
        assertEquals((Integer)10, criteria.getMaxAppVersion(OperatingSystem.IOS));
        assertEquals(Sets.newHashSet("requiredGroup"), criteria.getAllOfGroups());
        assertEquals(Sets.newHashSet("prohibitedGroup"), criteria.getNoneOfGroups());
    }
    
    @Test
    public void getSubpopulation() throws Exception {
        TestUtils.mockPlay().mock();
        
        Subpopulation subpop = Subpopulation.create();
        subpop.setGuidString("AAA");
        doReturn(subpop).when(subpopService).getSubpopulation(STUDY_IDENTIFIER, SUBPOP_GUID);
        
        Result result = controller.getSubpopulation(SUBPOP_GUID.getGuid());
        TestUtils.assertResult(result, 200);

        // Serialization has been tested elsewhere, we're not testing it all here, we're just
        // verifying the object is returned in the API
        String json = Helpers.contentAsString(result);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        assertEquals("Subpopulation", node.get("type").asText());
        assertEquals("AAA", node.get("guid").asText());
        assertNull(node.get("studyIdentifier"));
        
        verify(subpopService).getSubpopulation(STUDY_IDENTIFIER, SUBPOP_GUID);
    }
    
    @Test
    public void getSubpopulationForResearcher() throws Exception {
        participant = new StudyParticipant.Builder().withRoles(Sets.newHashSet(Roles.RESEARCHER)).build();
        session.setParticipant(participant);
        TestUtils.mockPlay().mock();
        
        Subpopulation subpop = Subpopulation.create();
        subpop.setGuidString("AAA");
        doReturn(subpop).when(subpopService).getSubpopulation(STUDY_IDENTIFIER, SUBPOP_GUID);

        // Does not throw UnauthorizedException.
        controller.getSubpopulation(SUBPOP_GUID.getGuid());
    }
    
    @Test
    public void deleteSubpopulationDefaultsToLogical() throws Exception {
        TestUtils.mockPlay().mock();

        Result result = controller.deleteSubpopulation(SUBPOP_GUID.getGuid(), null);
        
        assertResult(result, 200, "Subpopulation has been deleted.");
        verify(subpopService).deleteSubpopulation(STUDY_IDENTIFIER, SUBPOP_GUID);
    }
    
    @Test
    public void deleteSubpopulationLogically() throws Exception {
        TestUtils.mockPlay().mock();

        Result result = controller.deleteSubpopulation(SUBPOP_GUID.getGuid(), "false");
        
        assertResult(result, 200, "Subpopulation has been deleted.");
        verify(subpopService).deleteSubpopulation(STUDY_IDENTIFIER, SUBPOP_GUID);
    }
    
    @Test
    public void deleteSubpopulationPhysically() throws Exception {
        participant = new StudyParticipant.Builder().withRoles(Sets.newHashSet(Roles.ADMIN)).build();
        session = new UserSession(participant);
        session.setStudyIdentifier(STUDY_IDENTIFIER);
        session.setAuthenticated(true);
        doReturn(session).when(controller).getSessionIfItExists();
        
        TestUtils.mockPlay().mock();

        Result result = controller.deleteSubpopulation(SUBPOP_GUID.getGuid(), "true");
        
        assertResult(result, 200, "Subpopulation has been deleted.");
        verify(subpopService).deleteSubpopulationPermanently(STUDY_IDENTIFIER, SUBPOP_GUID);
    }
    
    @Test
    public void deleteSubpopulationPhysicallyIsLogicalForResearcher() throws Exception {
        controller.deleteSubpopulation(SUBPOP_GUID.getGuid(), "true");
        
        verify(subpopService).deleteSubpopulation(STUDY_IDENTIFIER, SUBPOP_GUID);
    }

    @Test(expected = UnauthorizedException.class)
    public void getAllSubpopulationsRequiresDeveloper() throws Exception {
        session.setParticipant(
                new StudyParticipant.Builder().copyOf(participant).withRoles(Sets.newHashSet(Roles.ADMIN)).build());
        
        controller.getAllSubpopulations("false");
    }
    
    @Test(expected = UnauthorizedException.class)
    public void createSubpopulationRequiresDeveloper() throws Exception {
        session.setParticipant(
                new StudyParticipant.Builder().copyOf(participant).withRoles(Sets.newHashSet(Roles.ADMIN)).build());
        
        controller.createSubpopulation();
    }
    
    @Test(expected = UnauthorizedException.class)
    public void updateSubpopulationRequiresDeveloper() throws Exception {
        session.setParticipant(
                new StudyParticipant.Builder().copyOf(participant).withRoles(Sets.newHashSet(Roles.ADMIN)).build());
        
        controller.updateSubpopulation(TestConstants.TEST_STUDY_IDENTIFIER);
    }

    @Test(expected = UnauthorizedException.class)
    public void getSubpopulationRequiresDeveloper() throws Exception {
        TestUtils.mockPlay().mock();
        session.setParticipant(new StudyParticipant.Builder().copyOf(participant).withRoles(Sets.newHashSet()).build());
        
        controller.getSubpopulation(TestConstants.TEST_STUDY_IDENTIFIER);
    }
    
    @Test(expected = UnauthorizedException.class)
    public void deleteSubpopulationRequiresDeveloper() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().copyOf(participant).withRoles(Sets.newHashSet()).build());
        
        controller.getSubpopulation(TestConstants.TEST_STUDY_IDENTIFIER);
    }
    
    private List<Subpopulation> createSubpopulationList() {
        Subpopulation subpop1 = Subpopulation.create();
        subpop1.setName("Name 1");
        Subpopulation subpop2 = Subpopulation.create();
        subpop2.setName("Name 2");
        return Lists.newArrayList(subpop1, subpop2);
    }
}
