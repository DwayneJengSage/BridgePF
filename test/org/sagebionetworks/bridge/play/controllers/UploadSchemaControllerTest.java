package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import play.mvc.Result;
import play.test.Helpers;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.services.UploadSchemaService;

public class UploadSchemaControllerTest {
    private static final String TEST_SCHEMA_ID = "controller-test-schema";
    private static final String TEST_SCHEMA_JSON = "{\n" +
                    "   \"name\":\"Controller Test Schema\",\n" +
                    "   \"revision\":3,\n" +
                    "   \"schemaId\":\"controller-test-schema\",\n" +
                    "   \"schemaType\":\"ios_data\",\n" +
                    "   \"fieldDefinitions\":[\n" +
                    "       {\n" +
                    "           \"name\":\"field-name\",\n" +
                    "           \"required\":true,\n" +
                    "           \"type\":\"STRING\"\n" +
                    "       }\n" +
                    "   ]\n" +
                    "}";

    @Test
    public void createV4() throws Exception {
        // mock service
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        ArgumentCaptor<UploadSchema> createdSchemaCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        when(mockSvc.createSchemaRevisionV4(eq(TestConstants.TEST_STUDY), createdSchemaCaptor.capture())).thenReturn(
                makeUploadSchemaForOutput());

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc, DEVELOPER);
        Result result = controller.createSchemaRevisionV4();
        TestUtils.assertResult(result, 201);
        assertSchemaInResult(result);
        assertSchemaInArgCaptor(createdSchemaCaptor);
    }

    @Test
    public void createSchema() throws Exception {
        // mock UploadSchemaService
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        ArgumentCaptor<UploadSchema> createdSchemaArgCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        when(mockSvc.createOrUpdateUploadSchema(eq(TestConstants.TEST_STUDY), createdSchemaArgCaptor.capture()))
                .thenReturn(makeUploadSchemaForOutput());

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc, DEVELOPER);
        Result result = controller.createOrUpdateUploadSchema();
        TestUtils.assertResult(result, 200);
        assertSchemaInResult(result);
        assertSchemaInArgCaptor(createdSchemaArgCaptor);
    }

    @Test
    public void deleteAllRevisionsOfUploadSchema() throws Exception {
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc, DEVELOPER, ADMIN);
        
        Result result = controller.deleteAllRevisionsOfUploadSchema("delete-schema", "false");
        TestUtils.assertResult(result, 200, "Schemas have been deleted.");
        verify(mockSvc).deleteUploadSchemaById(TestConstants.TEST_STUDY, "delete-schema");
    }
    
    @Test
    public void deleteAllRevisionsOfUploadSchemaPermanently() throws Exception {
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc, DEVELOPER, ADMIN);
        
        Result result = controller.deleteAllRevisionsOfUploadSchema("delete-schema", "true");
        TestUtils.assertResult(result, 200, "Schemas have been deleted.");
        verify(mockSvc).deleteUploadSchemaByIdPermanently(TestConstants.TEST_STUDY, "delete-schema");
    }
    
    @Test
    public void deleteAllRevisionsOfUploadSchemaPermanentlyForDeveloperIsLogical() throws Exception {
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithServiceWithoutSecondRole(mockSvc, DEVELOPER, ADMIN);
        
        Result result = controller.deleteAllRevisionsOfUploadSchema("delete-schema", "true");
        TestUtils.assertResult(result, 200, "Schemas have been deleted.");
        verify(mockSvc).deleteUploadSchemaById(TestConstants.TEST_STUDY, "delete-schema");
    }
    
    @Test
    public void deleteSchemaRevision() throws Exception {
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc, DEVELOPER, ADMIN);
        
        Result result = controller.deleteSchemaRevision("delete-schema", 4, "false");
        TestUtils.assertResult(result, 200, "Schema revision has been deleted.");
        verify(mockSvc).deleteUploadSchemaByIdAndRevision(TestConstants.TEST_STUDY, "delete-schema", 4);
    }
    
    @Test
    public void deleteSchemaRevisionPermanently() throws Exception {
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc, DEVELOPER, ADMIN);
        
        Result result = controller.deleteSchemaRevision("delete-schema", 4, "true");
        TestUtils.assertResult(result, 200, "Schema revision has been deleted.");
        verify(mockSvc).deleteUploadSchemaByIdAndRevisionPermanently(TestConstants.TEST_STUDY, "delete-schema", 4);
    }
    
    @Test
    public void deleteSchemaRevisionPermanentlyForDeveloperIsLogical() throws Exception {
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithServiceWithoutSecondRole(mockSvc, DEVELOPER, ADMIN);
        
        Result result = controller.deleteSchemaRevision("delete-schema", 4, "true");
        TestUtils.assertResult(result, 200, "Schema revision has been deleted.");
        // We do not call the permanent delete, we call the logical delete, as the user is a developer.
        verify(mockSvc).deleteUploadSchemaByIdAndRevision(TestConstants.TEST_STUDY, "delete-schema", 4);
    }
    
    @Test
    public void getSchemaById() throws Exception {
        // mock UploadSchemaService
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        when(mockSvc.getUploadSchema(TestConstants.TEST_STUDY, TEST_SCHEMA_ID)).thenReturn(
                makeUploadSchemaForOutput());

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc, DEVELOPER);
        Result result = controller.getUploadSchema(TEST_SCHEMA_ID);
        TestUtils.assertResult(result, 200);
        assertSchemaInResult(result);
    }

    @Test
    public void getSchemaByIdAndRev() throws Exception {
        // mock UploadSchemaService
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        when(mockSvc.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, TEST_SCHEMA_ID, 1)).thenReturn(
                makeUploadSchemaForOutput());

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc, DEVELOPER, WORKER);
        Result result = controller.getUploadSchemaByIdAndRev(TEST_SCHEMA_ID, 1);
        TestUtils.assertResult(result, 200);
        assertSchemaInResult(result);
    }

    @Test
    public void getByStudyAndSchemaAndRev() throws Exception {
        // mock UploadSchemaService
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        when(mockSvc.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, TEST_SCHEMA_ID, 1)).thenReturn(
                makeUploadSchemaForOutput());

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc, WORKER);
        Result result = controller.getUploadSchemaByStudyAndSchemaAndRev(TestConstants.TEST_STUDY_IDENTIFIER,
                TEST_SCHEMA_ID, 1);
        TestUtils.assertResult(result, 200);

        // Unlike the other methods, this also returns study ID
        String resultJson = Helpers.contentAsString(result);
        UploadSchema resultSchema = BridgeObjectMapper.get().readValue(resultJson, UploadSchema.class);
        assertEquals(TEST_SCHEMA_ID, resultSchema.getSchemaId());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, resultSchema.getStudyId());
    }

    @Test
    public void getSchemasForStudyNoDeleted() throws Exception {
        // mock UploadSchemaService
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        when(mockSvc.getUploadSchemasForStudy(TestConstants.TEST_STUDY, false)).thenReturn(ImmutableList.of(
                makeUploadSchemaForOutput()));

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc, DEVELOPER, RESEARCHER);
        Result result = controller.getUploadSchemasForStudy("false");
        TestUtils.assertResult(result, 200);

        String resultJson = Helpers.contentAsString(result);
        JsonNode resultNode = BridgeObjectMapper.get().readTree(resultJson);
        assertEquals("ResourceList", resultNode.get("type").textValue());
        assertEquals(1, resultNode.get("items").size());

        JsonNode itemListNode = resultNode.get("items");
        assertEquals(1, itemListNode.size());

        UploadSchema resultSchema = BridgeObjectMapper.get().treeToValue(itemListNode.get(0), UploadSchema.class);
        assertEquals(TEST_SCHEMA_ID, resultSchema.getSchemaId());
        assertNull(resultSchema.getStudyId());
    }

    @Test
    public void getSchemasForStudyIncludeDeleted() throws Exception {
        // mock UploadSchemaService
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        when(mockSvc.getUploadSchemasForStudy(TestConstants.TEST_STUDY, true)).thenReturn(ImmutableList.of(
                makeUploadSchemaForOutput()));

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc, DEVELOPER, RESEARCHER);
        Result result = controller.getUploadSchemasForStudy("true");
        TestUtils.assertResult(result, 200);
        
        verify(mockSvc).getUploadSchemasForStudy(TestConstants.TEST_STUDY, true);
    }
    
    @Test
    public void getSchemasForStudyDefaultNoDeleted() throws Exception {
        // mock UploadSchemaService
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        when(mockSvc.getUploadSchemasForStudy(TestConstants.TEST_STUDY, true)).thenReturn(ImmutableList.of(
                makeUploadSchemaForOutput()));

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc, DEVELOPER, RESEARCHER);
        Result result = controller.getUploadSchemasForStudy(null);
        TestUtils.assertResult(result, 200);
        
        verify(mockSvc).getUploadSchemasForStudy(TestConstants.TEST_STUDY, false);
    }
    
    @Test
    public void getAllRevisionsOfASchemaExcludeDeleted() throws Exception {
        String schemaId = "controller-test-schema";

        // Create a couple of revisions
        UploadSchema schema1 = makeUploadSchemaForOutput(1);
        UploadSchema schema2 = makeUploadSchemaForOutput(2);
        UploadSchema schema3 = makeUploadSchemaForOutput(3);

        // mock UploadSchemaService
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        when(mockSvc.getUploadSchemaAllRevisions(TestConstants.TEST_STUDY, schemaId, false)).thenReturn(ImmutableList.of(
                schema3, schema2, schema1));

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc, DEVELOPER);
        Result result = controller.getUploadSchemaAllRevisions(schemaId, "false");
        TestUtils.assertResult(result, 200);

        String resultJson = Helpers.contentAsString(result);
        JsonNode resultNode = BridgeObjectMapper.get().readTree(resultJson);
        assertEquals("ResourceList", resultNode.get("type").textValue());
        assertEquals(3, resultNode.get("items").size());

        JsonNode itemsNode = resultNode.get("items");
        assertEquals(3, itemsNode.size());

        // Schemas are returned in reverse order.
        UploadSchema returnedSchema3 = BridgeObjectMapper.get().treeToValue(itemsNode.get(0), UploadSchema.class);
        assertEquals(3, returnedSchema3.getRevision());
        assertEquals(TEST_SCHEMA_ID, returnedSchema3.getSchemaId());
        assertNull(returnedSchema3.getStudyId());

        UploadSchema returnedSchema2 = BridgeObjectMapper.get().treeToValue(itemsNode.get(1), UploadSchema.class);
        assertEquals(2, returnedSchema2.getRevision());
        assertEquals(TEST_SCHEMA_ID, returnedSchema2.getSchemaId());
        assertNull(returnedSchema2.getStudyId());

        UploadSchema returnedSchema1 = BridgeObjectMapper.get().treeToValue(itemsNode.get(2), UploadSchema.class);
        assertEquals(1, returnedSchema1.getRevision());
        assertEquals(TEST_SCHEMA_ID, returnedSchema1.getSchemaId());
        assertNull(returnedSchema1.getStudyId());
    }

    @Test
    public void getAllRevisionsOfASchemaExcludeDeletedByDefault() throws Exception {
        String schemaId = "controller-test-schema";

        // mock UploadSchemaService
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        when(mockSvc.getUploadSchemaAllRevisions(TestConstants.TEST_STUDY, schemaId, false)).thenReturn(ImmutableList.of());

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc, DEVELOPER);
        Result result = controller.getUploadSchemaAllRevisions(schemaId, null);
        TestUtils.assertResult(result, 200);

        verify(mockSvc).getUploadSchemaAllRevisions(TestConstants.TEST_STUDY, schemaId, false);
    }

    @Test
    public void getAllRevisionsOfASchemaIncludeDeleted() throws Exception {
        String schemaId = "controller-test-schema";

        // mock UploadSchemaService
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        when(mockSvc.getUploadSchemaAllRevisions(TestConstants.TEST_STUDY, schemaId, false)).thenReturn(ImmutableList.of());

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc, DEVELOPER);
        Result result = controller.getUploadSchemaAllRevisions(schemaId, "true");
        TestUtils.assertResult(result, 200);

        verify(mockSvc).getUploadSchemaAllRevisions(TestConstants.TEST_STUDY, schemaId, true);
    }
    
    @Test
    public void updateV4() throws Exception {
        // mock service
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        ArgumentCaptor<UploadSchema> updatedSchemaCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        when(mockSvc.updateSchemaRevisionV4(eq(TestConstants.TEST_STUDY), eq(TEST_SCHEMA_ID), eq(1),
                updatedSchemaCaptor.capture())).thenReturn(makeUploadSchemaForOutput());

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc, DEVELOPER);
        Result result = controller.updateSchemaRevisionV4(TEST_SCHEMA_ID, 1);
        TestUtils.assertResult(result, 200);
        assertSchemaInResult(result);
        assertSchemaInArgCaptor(updatedSchemaCaptor);
    }

    private static UploadSchemaController setupControllerWithServiceWithoutSecondRole(UploadSchemaService svc, Roles role1, Roles role2) throws Exception {
        // mock session
        UserSession mockSession = new UserSession();
        mockSession.setStudyIdentifier(TestConstants.TEST_STUDY);
        mockSession.setParticipant(new StudyParticipant.Builder().withRoles(Sets.newHashSet(role1)).build());

        // mock request JSON
        TestUtils.mockPlay().withJsonBody(TEST_SCHEMA_JSON).mock();

        // spy controller
        UploadSchemaController controller = spy(new UploadSchemaController());
        controller.setUploadSchemaService(svc);
        doReturn(mockSession).when(controller).getAuthenticatedSession(role1, role2);
        return controller;
    }
    
    private static UploadSchemaController setupControllerWithService(UploadSchemaService svc, Roles role1, Roles role2) throws Exception {
        // mock session
        UserSession mockSession = new UserSession();
        mockSession.setStudyIdentifier(TestConstants.TEST_STUDY);
        mockSession.setParticipant(new StudyParticipant.Builder().withRoles(Sets.newHashSet(role1, role2)).build());

        // mock request JSON
        TestUtils.mockPlay().withJsonBody(TEST_SCHEMA_JSON).mock();

        // spy controller
        UploadSchemaController controller = spy(new UploadSchemaController());
        controller.setUploadSchemaService(svc);
        doReturn(mockSession).when(controller).getAuthenticatedSession(role1, role2);
        return controller;
    }
    
    private static UploadSchemaController setupControllerWithService(UploadSchemaService svc, Roles role1) throws Exception {
        // mock session
        UserSession mockSession = new UserSession();
        mockSession.setStudyIdentifier(TestConstants.TEST_STUDY);
        mockSession.setParticipant(new StudyParticipant.Builder().withRoles(Sets.newHashSet(role1)).build());

        // mock request JSON
        TestUtils.mockPlay().withJsonBody(TEST_SCHEMA_JSON).mock();

        // spy controller
        UploadSchemaController controller = spy(new UploadSchemaController());
        controller.setUploadSchemaService(svc);
        controller.setBridgeConfig(mock(BridgeConfig.class));
        doReturn(mockSession).when(controller).getAuthenticatedSession(role1);
        return controller;
    }

    private static UploadSchema makeUploadSchemaForOutput() throws Exception {
        return makeUploadSchemaForOutput(3);
    }
    
    private static UploadSchema makeUploadSchemaForOutput(int revision) throws Exception {
        ObjectNode node = (ObjectNode)BridgeObjectMapper.get().readTree(TEST_SCHEMA_JSON);
        node.put("revision", revision);

        // Server returns schemas with study IDs (which are filtered out selectively in some methods).
        node.put("studyId", TestConstants.TEST_STUDY_IDENTIFIER);

        return BridgeObjectMapper.get().convertValue(node, UploadSchema.class);
    }

    private static void assertSchemaInResult(Result result) throws Exception {
        // JSON validation is already tested, so just check obvious things like schema ID
        // Also, (most) method results don't include study ID
        String jsonText = Helpers.contentAsString(result);
        UploadSchema schema = BridgeObjectMapper.get().readValue(jsonText, UploadSchema.class);
        assertEquals(TEST_SCHEMA_ID, schema.getSchemaId());
        assertNull(schema.getStudyId());
    }

    private static void assertSchemaInArgCaptor(ArgumentCaptor<UploadSchema> argCaptor) {
        // Similarly, just check schema ID
        UploadSchema arg = argCaptor.getValue();
        assertEquals(TEST_SCHEMA_ID, arg.getSchemaId());
    }
}
