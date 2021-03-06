package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.models.OperatingSystem.ANDROID;

import java.util.HashSet;
import java.util.List;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoAppConfigDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.schedules.SchemaReference;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoAppConfigDaoTest {
    
    private static final HashSet<String> SET_B = Sets.newHashSet("c","d");
    private static final HashSet<String> SET_A = Sets.newHashSet("a","b");
    private static final List<SurveyReference> SURVEY_REFS = new ImmutableList.Builder<SurveyReference>()
            .add(new SurveyReference("surveyA", BridgeUtils.generateGuid(), DateTime.now()))
            .add(new SurveyReference("surveyB", BridgeUtils.generateGuid(), DateTime.now())).build();
    private static final List<SchemaReference> SCHEMA_REFS = new ImmutableList.Builder<SchemaReference>()
            .add(new SchemaReference("schemaA", 1))
            .add(new SchemaReference("schemaB", 2)).build();
    
    private static final StudyIdentifier STUDY_ID = new StudyIdentifierImpl(TestUtils.randomName(DynamoAppConfigDaoTest.class));
    
    @Resource
    DynamoAppConfigDao dao;
    
    @After
    public void after() {
        List<AppConfig> appConfigs = dao.getAppConfigs(STUDY_ID, true);
        for (AppConfig oneConfig : appConfigs) {
            dao.deleteAppConfigPermanently(STUDY_ID, oneConfig.getGuid());
        }
        assertTrue(dao.getAppConfigs(STUDY_ID, false).isEmpty());
    }    

    private AppConfig createAppConfig() { 
        Criteria criteria;
        criteria = TestUtils.createCriteria(2, 8, SET_A, SET_B);
        criteria.setMinAppVersion(ANDROID, 10);
        criteria.setMaxAppVersion(ANDROID, 15);
        criteria.setLanguage("fr");

        JsonNode clientData = TestUtils.getClientData();
        
        AppConfig config = AppConfig.create();
        config.setLabel("Label");
        config.setGuid(BridgeUtils.generateGuid());
        config.setCriteria(criteria);
        config.setSurveyReferences(SURVEY_REFS);
        config.setSchemaReferences(SCHEMA_REFS);
        config.setClientData(clientData);
        config.setStudyId(STUDY_ID.getIdentifier());
        return config;
    }
    
    @Test
    public void crudAppConfig() {
        AppConfig config = createAppConfig();
        
        // save new app config
        AppConfig saved = dao.createAppConfig(config);
        assertEquals(config, saved);
        
        // get the config, verify it
        AppConfig retrieved = dao.getAppConfig(STUDY_ID, saved.getGuid());
        assertEquals(retrieved, config);
        
        // update a changed version of the record
        retrieved.setSchemaReferences(null);
        retrieved.setSurveyReferences(null);
        
        AppConfig updated = dao.updateAppConfig(retrieved);
        retrieved = dao.getAppConfig(STUDY_ID, saved.getGuid());
        assertTrue(retrieved.getSurveyReferences().isEmpty());
        assertTrue(retrieved.getSchemaReferences().isEmpty());
        
        // make a copy.
        saved.setGuid(BridgeUtils.generateGuid());
        saved.setVersion(null);
        AppConfig copy = dao.createAppConfig(saved);
        
        // retrieve list of 2 records
        List<AppConfig> lists = dao.getAppConfigs(STUDY_ID, false);
        assertEquals(2, lists.size());
        
        AppConfig first = dao.getAppConfig(STUDY_ID, saved.getGuid());
        assertNotNull(first);
        
        AppConfig second = dao.getAppConfig(STUDY_ID, copy.getGuid());
        assertNotNull(second);
        
        // delete one record
        dao.deleteAppConfig(STUDY_ID, updated.getGuid());
        AppConfig deletedConfig = dao.getAppConfig(STUDY_ID, updated.getGuid());
        assertTrue(deletedConfig.isDeleted());
        
        // Should now only be one config in the list call
        lists = dao.getAppConfigs(STUDY_ID, false);
        assertEquals(1, lists.size());
        assertFalse(lists.get(0).isDeleted());
        
        // Or two if we toggle the flat
        lists = dao.getAppConfigs(STUDY_ID, true);
        assertEquals(2, lists.size());
        
        // deleteAppConfigPermanently is used in test cleanup, and then verified
    }
    
    @Test
    public void logicalDeleteLeavesConfigInDatabase() {
        AppConfig config = createAppConfig();
        AppConfig saved = dao.createAppConfig(config);
        dao.deleteAppConfig(STUDY_ID, saved.getGuid());
        
        AppConfig loaded = dao.loadAppConfig(STUDY_ID, saved.getGuid());
        assertTrue(loaded.isDeleted());
    }
    
    @Test
    public void createAppConfigCannotBeDeleted() {
        AppConfig config = createAppConfig();
        config.setDeleted(true);
        AppConfig saved = dao.createAppConfig(config);
        
        assertFalse(saved.isDeleted());
    }
    
    @Test
    public void updatingPermanentlyDeletedConfigThrowsError() {
        AppConfig config = createAppConfig();
        AppConfig saved = dao.createAppConfig(config);
        dao.deleteAppConfigPermanently(STUDY_ID, saved.getGuid());
        try {
            dao.updateAppConfig(saved);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
            
        }
    }
    
    @Test
    public void deletePermanentlyMissingAppConfigThrows() {
        AppConfig config = createAppConfig();
        AppConfig saved = dao.createAppConfig(config);
        
        dao.deleteAppConfigPermanently(STUDY_ID, saved.getGuid());
        try {
            dao.deleteAppConfig(STUDY_ID, saved.getGuid());
            fail("Should have thrown an exception");
        } catch(EntityNotFoundException e) {
        }
    }
    
    @Test
    public void deletePermanentlyLogicallyDeletedAppConfig() {
        AppConfig config = createAppConfig();
        AppConfig saved = dao.createAppConfig(config);

        dao.deleteAppConfig(STUDY_ID, saved.getGuid());
        dao.deleteAppConfigPermanently(STUDY_ID, saved.getGuid());
        try {
            dao.getAppConfig(STUDY_ID, saved.getGuid());
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
        }
    }
    
    @Test
    public void getAppConfigIncludesLogicallyDeleted() {
        AppConfig config = createAppConfig();
        AppConfig saved = dao.createAppConfig(config);
        dao.deleteAppConfig(STUDY_ID, saved.getGuid());

        AppConfig returned = dao.getAppConfig(STUDY_ID, saved.getGuid());
        assertTrue(returned.isDeleted());
    }
    
    @Test
    public void getAppConfigsIncludesLogicallyDeleted() {
        AppConfig config = createAppConfig();
        AppConfig saved = dao.createAppConfig(config);
        dao.deleteAppConfig(STUDY_ID, saved.getGuid());

        List<AppConfig> configs = dao.getAppConfigs(STUDY_ID, true);
        assertEquals(saved.getGuid(), configs.get(0).getGuid());
    }
    
    @Test
    public void getAppConfigsExcludesLogicallyDeleted() {
        AppConfig config = createAppConfig();
        AppConfig saved = dao.createAppConfig(config);
        dao.deleteAppConfig(STUDY_ID, saved.getGuid());

        List<AppConfig> configs = dao.getAppConfigs(STUDY_ID, false);
        assertTrue(configs.isEmpty());
    }
    
    @Test
    public void updateLogicallyDeletedAppConfigThrows() {
        AppConfig config = createAppConfig();
        AppConfig saved = dao.createAppConfig(config);
        dao.deleteAppConfig(STUDY_ID, saved.getGuid());
        
        // must do this to get the current version #
        AppConfig retrieved = dao.getAppConfig(STUDY_ID, saved.getGuid());
        retrieved.setLabel("Some trivial change");
        // But note we are not undeleting it
        
        try {
            dao.updateAppConfig(retrieved);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
            
        }
        retrieved = dao.getAppConfig(STUDY_ID, saved.getGuid());
        assertEquals("Label", retrieved.getLabel());
    }
    
    @Test
    public void updateCanUndeleteAppConfig() {
        AppConfig config = createAppConfig();
        AppConfig saved = dao.createAppConfig(config);

        saved.setDeleted(true);
        AppConfig returned = dao.updateAppConfig(saved);
        
        returned.setDeleted(false);
        AppConfig returned2 = dao.updateAppConfig(saved);
        
        assertFalse(returned2.isDeleted());
        assertFalse(dao.getAppConfig(STUDY_ID, saved.getGuid()).isDeleted());
    }
    
    @Test
    public void updateCanDeleteAppConfig() {
        AppConfig config = createAppConfig();
        AppConfig saved = dao.createAppConfig(config);

        saved.setDeleted(true);
        AppConfig returned = dao.updateAppConfig(saved);
        
        assertTrue(returned.isDeleted());
        assertTrue(dao.getAppConfig(STUDY_ID, saved.getGuid()).isDeleted());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void deleteLogicallyDeletedAppConfigThrows() {
        AppConfig config = createAppConfig();
        AppConfig saved = dao.createAppConfig(config);
        
        dao.deleteAppConfig(STUDY_ID, saved.getGuid());
        dao.deleteAppConfig(STUDY_ID, saved.getGuid());
    }
    
    @Test
    public void deleteAppConfig() {
        AppConfig config = createAppConfig();
        AppConfig saved = dao.createAppConfig(config);
        
        dao.deleteAppConfig(STUDY_ID, saved.getGuid());
        
        assertTrue(dao.getAppConfig(STUDY_ID, saved.getGuid()).isDeleted());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void deleteAppConfigMissingAppConfigThrows() {
        dao.deleteAppConfig(STUDY_ID, "some-junk");
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void deleteAppConfigPermanently() {
        AppConfig config = createAppConfig();
        AppConfig saved = dao.createAppConfig(config);
        
        dao.deleteAppConfigPermanently(STUDY_ID, saved.getGuid());
        
        dao.getAppConfig(STUDY_ID, saved.getGuid());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void deleteAppConfigPermanentlyMissingAppConfigThrows() {
        dao.deleteAppConfigPermanently(STUDY_ID, "missingGuid");
    }
}
