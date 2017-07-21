package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Resource;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifierInfo;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoExternalIdDaoTest {
    
    private static final List<String> EXT_IDS = Lists.newArrayList("AAA", "BBB", "CCC");
    
    @Resource
    private DynamoExternalIdDao dao;
    
    @Resource(name = "externalIdDdbMapper")
    private DynamoDBMapper mapper;
    
    private StudyIdentifier studyId;

    @Before
    public void before() {
        studyId = new StudyIdentifierImpl(TestUtils.randomName(DynamoExternalIdDaoTest.class));
        dao.addExternalIds(studyId, EXT_IDS);
    }
    
    @After
    public void after() {
        dao.deleteExternalIds(studyId, EXT_IDS);
    }
    
    @Test(expected = BadRequestException.class)
    public void addCannotExceedLimit() {
        // Limit is set to 10. Make a list of 11 external IDs.
        List<String> extIdList = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            extIdList.add(String.valueOf(i));
        }
        dao.addExternalIds(studyId, extIdList);
    }

    @Test
    public void cannotAddExistingIdentifiers() {
        dao.assignExternalId(studyId, "AAA", "healthCode");
        
        dao.addExternalIds(studyId, EXT_IDS);
        
        DynamoDBQueryExpression<DynamoExternalIdentifier> query = new DynamoDBQueryExpression<>();
        query.withScanIndexForward(false);
        query.withHashKeyValues(new DynamoExternalIdentifier(studyId, null));
        
        PaginatedQueryList<? extends DynamoExternalIdentifier> page = mapper.query(DynamoExternalIdentifier.class, query);
        Set<String> ids = page.stream().map(DynamoExternalIdentifier::getIdentifier).collect(Collectors.toSet());
        assertEquals(Sets.newHashSet("AAA","BBB","CCC"), ids);
        
        // Just as importantly, AAA is still assigned to "healthCode"
        DynamoExternalIdentifier keyObject = new DynamoExternalIdentifier(studyId, "AAA");
        DynamoExternalIdentifier identifier = mapper.load(keyObject);
        assertEquals("healthCode", identifier.getHealthCode());
    }
    
    @Test
    public void reservationSucceedsFirstTime() {
        dao.reserveExternalId(studyId, "AAA");
        
        DynamoExternalIdentifier keyObject = new DynamoExternalIdentifier(studyId, "AAA");
        DynamoExternalIdentifier identifier = mapper.load(keyObject);
        assertTrue(identifier.getReservation() > 0L);
    }
    
    @Test
    public void reservationSucceedsAfterLockExpires() throws Exception {
        dao.reserveExternalId(studyId, "AAA");

        // Timeout is 30 seconds. Sleep for 31 seconds.
        Thread.sleep(31000);
        dao.reserveExternalId(studyId, "AAA");
    }
    
    @Test(expected = EntityAlreadyExistsException.class)
    public void reservationFailsOnHealthCodeAssigned() {
        dao.assignExternalId(studyId, "AAA", "some-health-code");
        
        dao.reserveExternalId(studyId, "AAA");
    }
    
    @Test(expected = EntityAlreadyExistsException.class)
    public void reservationFailsOnReservationWindow() {
        dao.reserveExternalId(studyId, "AAA");
        dao.reserveExternalId(studyId, "AAA");
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void reservationFailsOnCodeDoesNotExist() {
        dao.reserveExternalId(studyId, "DDD");
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void reservationFailsOnCodeOutsideStudy() {
        StudyIdentifier studyId = new StudyIdentifierImpl("some-other-study");
        dao.reserveExternalId(studyId, "AAA");
    }
    
    @Test
    public void canAssignExternalId() {
        dao.assignExternalId(studyId, "AAA", "healthCode");
        
        DynamoExternalIdentifier keyObject = new DynamoExternalIdentifier(studyId, "AAA");
        DynamoExternalIdentifier identifier = mapper.load(keyObject);
        assertEquals("healthCode", identifier.getHealthCode());
        assertEquals(0, identifier.getReservation());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void assignMissingExternalIdThrowException() {
        dao.assignExternalId(studyId, "DDD", "healthCode");
    }
    
    @Test
    public void canReassignHealthCodeSafely() {
        // Well-behaved client code shouldn't do this, but if it happens it does not throw an exception
        dao.assignExternalId(studyId, "AAA", "healthCode");
        dao.assignExternalId(studyId, "AAA", "healthCode");
    }
    
    @Test(expected = EntityAlreadyExistsException.class)
    public void identifierCannotBeAssignedTwice() {
        // Well-behaved client code shouldn't do this, but if it happens, it will not succeed.
        dao.assignExternalId(studyId, "AAA", "healthCode");
        dao.assignExternalId(studyId, "AAA", "differentHealthCode");
    }
    
    @Test
    public void identifierCanBeUnassigned() {
        dao.assignExternalId(studyId, "AAA", "healthCode");
        
        dao.unassignExternalId(studyId, "AAA");
        
        DynamoExternalIdentifier keyObject = new DynamoExternalIdentifier(studyId, "AAA");
        DynamoExternalIdentifier identifier = mapper.load(keyObject);
        assertNull(identifier.getHealthCode());
        assertEquals(0L, identifier.getReservation());
    }
    
    @Test
    public void unassignFailsQuietly() {
        dao.unassignExternalId(studyId, "AAA"); // never assigned
        dao.unassignExternalId(studyId, "DDD"); // doesn't exist
    }
    
    @Test(expected = BadRequestException.class)
    public void pageSizeCannotBeNegative() {
        dao.getExternalIds(studyId, null, -100, null, null);
    }
    
    @Test(expected = BadRequestException.class)
    public void pageSizeCannotBeGreaterThan100() {
        dao.getExternalIds(studyId, null, 101, null, null);
    }
    
    @Test
    public void canGetIds() {
        ForwardCursorPagedResourceList<ExternalIdentifierInfo> page = dao.getExternalIds(studyId, null, 10, null, null);
        
        assertEquals(3, page.getItems().size());
        assertEquals(10, page.getPageSize());
        assertNull(page.getOffsetKey());
    }
    
    @Test
    public void canFilterIds() {
        List<String> moreIds = Lists.newArrayList("aaa", "bbb", "ccc", "DDD", "AEE", "AFF");
        try {
            dao.addExternalIds(studyId, moreIds);
            
            // AAA, AEE, AFF
            ForwardCursorPagedResourceList<ExternalIdentifierInfo> page = dao.getExternalIds(studyId, null, 10, "A", null);
            assertEquals(3, page.getItems().size());
            assertEquals(10, page.getPageSize());
            assertEquals("A", page.getRequestParams().get("idFilter"));
            assertNull(page.getOffsetKey());

            // Nothing matches this filter
            page = dao.getExternalIds(studyId, null, 10, "ZZZ", null);
            assertEquals(0, page.getItems().size());
            assertEquals(10, page.getPageSize());
            assertEquals("ZZZ", page.getRequestParams().get("idFilter"));
            assertNull(page.getOffsetKey());
            
            dao.assignExternalId(studyId, "AAA", "healthCode1");
            dao.assignExternalId(studyId, "BBB", "healthCode1");

            page = dao.getExternalIds(studyId, null, 10, null, Boolean.TRUE);
            assertEquals(2, page.getItems().size());
            assertEquals(toSet(true, "AAA", "BBB"), Sets.newHashSet(page.getItems()));
        } finally {
            dao.deleteExternalIds(studyId, moreIds);
        }
    }
    
    @Test
    public void canRetrieveCurrentAndNextPage() {
        // Add more external IDs.
        List<String> moreIds1 = ImmutableList.of("DDD", "EEE", "FFF", "GGG", "HHH", "III", "JJJ", "KKK", "LLL", "MMM");
        List<String> moreIds2 = ImmutableList.of("NNN", "OOO", "PPP", "QQQ", "RRR", "SSS", "TTT", "UUU", "VVV", "WWW");
        List<String> moreIds3 = ImmutableList.of("XXX", "YYY", "ZZZ");
        try {
            dao.addExternalIds(studyId, moreIds1);
            dao.addExternalIds(studyId, moreIds2);
            dao.addExternalIds(studyId, moreIds3);

            ForwardCursorPagedResourceList<ExternalIdentifierInfo> page = dao.getExternalIds(studyId, null, 5, null, null);
            assertEquals(5, page.getItems().size());
            assertEquals("EEE", page.getOffsetKey());
            assertEquals(toSet(false, "AAA","BBB","CCC","DDD","EEE"), Sets.newHashSet(page.getItems()));
            
            page = dao.getExternalIds(studyId, page.getOffsetKey(), 5, null, null);
            assertEquals(toSet(false, "FFF","GGG","HHH","III","JJJ"), Sets.newHashSet(page.getItems()));
            
            page = dao.getExternalIds(studyId, page.getOffsetKey(), 5, null, null);
            assertEquals(toSet(false, "KKK","LLL","MMM","NNN","OOO"), Sets.newHashSet(page.getItems()));
            
            page = dao.getExternalIds(studyId, page.getOffsetKey(), 15, null, null);
            assertEquals(toSet(false, "PPP", "QQQ", "RRR", "SSS", "TTT", "UUU", "VVV", "WWW", "XXX", "YYY", "ZZZ"),
                    Sets.newHashSet(page.getItems()));
            assertNull(page.getOffsetKey());
        } finally {
            try {
                dao.deleteExternalIds(studyId, moreIds1);
            } catch (Exception ex) {
                // suppress cleanup exception
            }

            try {
                dao.deleteExternalIds(studyId, moreIds2);
            } catch (Exception ex) {
                // suppress cleanup exception
            }

            try {
                dao.deleteExternalIds(studyId, moreIds3);
            } catch (Exception ex) {
                // suppress cleanup exception
            }
        }
    }
    
    @Test
    public void pagingWithFilterResetsInapplicableOffsetKey() {
        ForwardCursorPagedResourceList<ExternalIdentifierInfo> page = dao.getExternalIds(studyId, "B", 5, "C", null);
        assertEquals(new ExternalIdentifierInfo("CCC", false), page.getItems().get(0));
        assertNull(page.getOffsetKey());
    }
    
    @Test
    public void pagingWithFilterLongerThanKeyWorks() {
        ForwardCursorPagedResourceList<ExternalIdentifierInfo> page = dao.getExternalIds(studyId, "CCCCC", 5, "CC", null);
        assertTrue(page.getItems().isEmpty());
    }
    
    @Test
    public void pagingWithFilterShorterThanKeyWorks() {
        ForwardCursorPagedResourceList<ExternalIdentifierInfo> page = dao.getExternalIds(studyId, "C", 5, "CC", null);
        assertEquals(new ExternalIdentifierInfo("CCC", false), page.getItems().get(0));
        assertNull(page.getOffsetKey());
    }
    
    // Sometimes paging fails when the total records divided by the page has no remainder. 
    // So last item on last page is the last record. Verify this works.
    @Test
    public void pagingWithNoRemainderWorks() {
        List<String> moreIds = Lists.newArrayList("DDD", "EEE", "FFF");
        try {
            dao.addExternalIds(studyId, moreIds);

            ForwardCursorPagedResourceList<ExternalIdentifierInfo> page = dao.getExternalIds(studyId, null, 3, null, null);
            assertEquals(3, page.getItems().size());
            assertNotNull(page.getOffsetKey());
            
            page = dao.getExternalIds(studyId, page.getOffsetKey(), 3, null, null);
            assertEquals(3, page.getItems().size());
            assertNull(page.getOffsetKey());
        } finally {
            dao.deleteExternalIds(studyId, moreIds);
        }
    }
    
    @Test
    public void retrieveUnassignedExcludesReserved() throws Exception {
        dao.assignExternalId(studyId, "AAA", "healthCode1");
        dao.reserveExternalId(studyId, "BBB"); // only reserved

        ForwardCursorPagedResourceList<ExternalIdentifierInfo> page = dao.getExternalIds(studyId, null, 5, null, Boolean.FALSE);
        assertEquals(1, page.getItems().size());
        assertEquals(new ExternalIdentifierInfo("CCC", false), page.getItems().get(0));

        // Timeout is 30 seconds. Sleep for 31 seconds.
        Thread.sleep(31000);
        page = dao.getExternalIds(studyId, null, 5, null, Boolean.FALSE);
        assertEquals(2, page.getItems().size());
        assertTrue(page.getItems().contains(new ExternalIdentifierInfo("BBB", false)));
        assertTrue(page.getItems().contains(new ExternalIdentifierInfo("CCC", false)));
    }
    
    @Test
    public void retrieveAssignedIncludesReserved() throws Exception {
        dao.assignExternalId(studyId, "AAA", "healthCode");
        dao.reserveExternalId(studyId, "BBB");

        ForwardCursorPagedResourceList<ExternalIdentifierInfo> page = dao.getExternalIds(studyId, null, 5, null, Boolean.TRUE);
        assertEquals(2, page.getItems().size());
        assertTrue(page.getItems().contains(new ExternalIdentifierInfo("AAA", true)));
        assertTrue(page.getItems().contains(new ExternalIdentifierInfo("BBB", true)));
        
        // Wait until lock is released, item is no longer in results that are considered assigned.
        Thread.sleep(31000);
        page = dao.getExternalIds(studyId, null, 5, null, Boolean.TRUE);
        assertEquals(1, page.getItems().size());
        assertTrue(page.getItems().contains(new ExternalIdentifierInfo("AAA", true)));
    }
    
    @Test
    public void getNextAvailableID() {
        // We should skip over reserved and assigned IDs to find a free one
        dao.assignExternalId(studyId, "AAA", "healthCode");
        dao.reserveExternalId(studyId, "BBB");

        ForwardCursorPagedResourceList<ExternalIdentifierInfo> ids = dao.getExternalIds(studyId, null, 1, null, Boolean.FALSE);
        
        assertEquals(1, ids.getItems().size());
        assertEquals("CCC", ids.getItems().get(0).getIdentifier());
    }

    private Set<ExternalIdentifierInfo> toSet(boolean isAssigned, String... infos) {
        Set<ExternalIdentifierInfo> set = Sets.newHashSetWithExpectedSize(infos.length);
        for (String identifier : infos) {
            set.add(new ExternalIdentifierInfo(identifier, isAssigned));
        }
        return set; 
    }
    
}
