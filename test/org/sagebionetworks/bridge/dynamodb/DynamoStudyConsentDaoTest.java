package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsent;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoStudyConsentDaoTest {
    
    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create("ABC");
    
    @Resource
    private DynamoStudyConsentDao studyConsentDao;

    @After
    public void after() {
        studyConsentDao.deleteAllConsents(SUBPOP_GUID);

        assertEquals(0, studyConsentDao.getConsents(SUBPOP_GUID).size());
    }

    @Test
    public void crudStudyConsentWithFileBasedContent() {
        long createdOn = DateUtils.getCurrentMillisFromEpoch();
        
        // Add consent version 1, inactive
        StudyConsent consent1 = studyConsentDao.addConsent(SUBPOP_GUID, SUBPOP_GUID+"."+createdOn, createdOn);
        assertEquals(createdOn, consent1.getCreatedOn());
        
        createdOn = DateUtils.getCurrentMillisFromEpoch();
        
        // Add version 2
        StudyConsent consent2 = studyConsentDao.addConsent(SUBPOP_GUID, SUBPOP_GUID+"."+createdOn, createdOn);
        assertEquals(createdOn, consent2.getCreatedOn());
        
        // The most recent consent should be version 2
        StudyConsent consent = studyConsentDao.getMostRecentConsent(SUBPOP_GUID);
        assertConsentsEqual(consent2, consent, false);

        // Can still get version 1 using its timestamp
        consent = studyConsentDao.getConsent(SUBPOP_GUID, consent1.getCreatedOn());
        assertConsentsEqual(consent1, consent, false);
        
        // Add a third consent to test list of consents
        createdOn = DateUtils.getCurrentMillisFromEpoch();
        final StudyConsent consent3 = studyConsentDao.addConsent(SUBPOP_GUID, SUBPOP_GUID+"."+createdOn, createdOn);
        
        // Get all consents. Should return in reverse order
        List<? extends StudyConsent> all = studyConsentDao.getConsents(SUBPOP_GUID);
        assertEquals(3, all.size());
        assertConsentsEqual(consent3, all.get(0), false);
        assertConsentsEqual(consent2, all.get(1), true);
        assertConsentsEqual(consent1, all.get(2), false);
    }
    
    @Test
    public void crudStudyConsentWithS3Content() throws Exception {
        long createdOn = DateUtils.getCurrentMillisFromEpoch();
        String key = SUBPOP_GUID + "." + createdOn;
        StudyConsent consent = studyConsentDao.addConsent(SUBPOP_GUID, key, createdOn);
        assertEquals(key, consent.getStoragePath());
    }
    
    private void assertConsentsEqual(StudyConsent existing, StudyConsent newOne, boolean isActive) {
        assertEquals(existing.getSubpopulationGuid(), newOne.getSubpopulationGuid());
        assertEquals(existing.getStoragePath(), newOne.getStoragePath());
        assertTrue(newOne.getCreatedOn() > 0);
    }
    
}