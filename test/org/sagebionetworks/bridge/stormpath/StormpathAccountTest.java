package org.sagebionetworks.bridge.stormpath;

import static org.junit.Assert.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.crypto.BridgeEncryptor;
import org.sagebionetworks.bridge.crypto.Encryptor;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import com.newrelic.agent.deps.com.google.common.collect.Lists;
import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.directory.CustomData;

public class StormpathAccountTest {
    
    private static final BridgeObjectMapper MAPPER = BridgeObjectMapper.get();
    
    private static final long UNIX_TIMESTAMP = DateTime.now().getMillis();
    
    @SuppressWarnings("serial")
    private class StubCustomData extends HashMap<String,Object> implements CustomData {
        @Override public String getHref() { return null; }
        @Override public void save() {}
        @Override public void delete() {}
        @Override public Date getCreatedAt() { return null; }
        @Override public Date getModifiedAt() { return null; }
    }

    private StubCustomData data;
    
    private Account account;
    
    private StormpathAccount acct;
    
    private String legacySignature;
    
    private ConsentSignature sig;
    
    @Before
    public void setUp() throws Exception {
        StudyIdentifier studyId = new StudyIdentifierImpl("foo");
        
        account = mock(Account.class);
        data = new StubCustomData(); 
        when(account.getCustomData()).thenReturn(data);

        BridgeEncryptor encryptor1 = mock(BridgeEncryptor.class);
        when(encryptor1.getVersion()).thenReturn(1);
        encryptDecryptValues(encryptor1, "1");
        
        BridgeEncryptor encryptor2 = mock(BridgeEncryptor.class);
        when(encryptor2.getVersion()).thenReturn(2);
        encryptDecryptValues(encryptor2, "2");
        
        // This must be a passthrough because we're not going to set the signature through
        // StormpathAccount, we're going to put a legacy state in the map that's stubbing out
        // The CustomData element, and then verify that we can retrieve and deserialize the consent
        // even without a version attribute.
        sig = new ConsentSignature.Builder().withName("Test").withBirthdate("1970-01-01").withImageData("test").withImageMimeType("image/png").withSignedOn(UNIX_TIMESTAMP).build();
        legacySignature = MAPPER.writeValueAsString(sig);
        
        SortedMap<Integer, BridgeEncryptor> encryptors = new TreeMap<>();
        encryptors.put(1, encryptor1);
        encryptors.put(2, encryptor2);
        
        acct = new StormpathAccount(studyId, account, encryptors);
    }
    
    private void encryptDecryptValues(final Encryptor encryptor, final String version) {
        when(encryptor.encrypt(any())).thenAnswer(invocation -> {
            return "encrypted-" + version + "-" + invocation.getArgumentAt(0, String.class);
        });
        when(encryptor.decrypt(any())).thenAnswer(invocation -> {
            String encValue = invocation.getArgumentAt(0, String.class);
            return (encValue == null) ? encValue : encValue.replace("encrypted-"+version+"-", "");
        });
    }
    
    @Test
    public void consentSignaturesEncrypted() throws Exception {
        List<ConsentSignature> signatures = Lists.newArrayList();
        signatures.add(new ConsentSignature.Builder().withName("Another Name").withBirthdate("1983-05-10").build());
        
        String json = BridgeObjectMapper.get().writeValueAsString(signatures);
        
        acct.setConsentSignatureHistory(signatures);
        
        assertEquals("encrypted-2-"+json, data.get("foo_consent_signature_history"));
        assertEquals(signatures, acct.getConsentSignatureHistory());
    }
    
    @Test
    public void basicFieldWorks() {
        when(account.getEmail()).thenReturn("test@test.com");
        
        acct.setEmail("test@test.com");
        assertEquals("test@test.com", acct.getEmail());
        
        verify(account).setEmail("test@test.com");
    }
    
    @Test
    public void basicFieldValueCanBeCleared() {
        when(account.getEmail()).thenReturn(null);
        
        acct.setEmail(null);
        assertNull(acct.getEmail());
        
        verify(account).setEmail(null);
    }
    
    @Test
    public void newSensitiveValueIsEncryptedWithLastEncryptor() {
        acct.setAttribute("phone", "111-222-3333");
        
        assertEquals("encrypted-2-111-222-3333", data.get("phone"));
        assertEquals("111-222-3333", acct.getAttribute("phone"));
    }
    
    @Test
    public void sensitiveValueWhenClearedRemovesVersionKey() {
        acct.setAttribute("phone", "111-222-3333");
        assertEquals(2, data.get("phone_version"));
        
        acct.setAttribute("phone", null);
        assertNull(data.get("phone"));
        assertNull(data.get("phone_version"));
    }
    
    @Test
    public void noValueSupported() {
        assertNull(acct.getAttribute("phone"));
    }
    
    @Test
    public void oldValuesDecryptedWithOldDecryptorAndEncryptedWithNewDecryptor() {
        data.put("phone", "encrypted-1-111-222-3333");
        data.put("phone_version", 1);

        assertEquals("111-222-3333", acct.getAttribute("phone"));
        
        acct.setAttribute("phone", acct.getAttribute("phone"));
        
        assertEquals("encrypted-2-111-222-3333", data.get("phone"));
        assertEquals("111-222-3333", acct.getAttribute("phone"));
    }
    
    @Test
    public void consentSignatureStoredAndEncrypted() {
        acct.setConsentSignature(sig);
        
        ConsentSignature restoredSig = acct.getConsentSignature();
        assertEquals("Test", restoredSig.getName());
        assertEquals("1970-01-01", restoredSig.getBirthdate());
        assertEquals("test", restoredSig.getImageData());
        assertEquals("image/png", restoredSig.getImageMimeType());
        assertEquals(UNIX_TIMESTAMP, restoredSig.getSignedOn());
    }
    
    @Test
    public void canClearKeyValue() {
        acct.setAttribute("phone", "111-222-3333");
        acct.setAttribute("phone", null);
        
        assertNull(acct.getAttribute("phone"));
    }
    
    @Test
    public void healthIdRetrievedWithNewVersion() {
        data.put("foo_code", "encrypted-1-aHealthId");
        data.put("foo_code_version", 1);
        
        String healthId = acct.getHealthId();
        assertEquals("aHealthId", healthId);
    }
    
    @Test
    public void healthIdRetrievedWithOldVersion() {
        data.put("foo_code", "encrypted-1-HealthId");
        data.put("fooversion", 1);
        
        String healthId = acct.getHealthId();
        assertEquals("HealthId", healthId);
    }
    
    @Test
    public void consentSignatureRetrievedWithNoVersion() throws Exception {
        // There is no version attribute for this. Can still retrieve it.
        data.put("foo_consent_signature", legacySignature);
        
        ConsentSignature restoredSig = acct.getConsentSignature();
        assertEquals("Test", restoredSig.getName());
        assertEquals("1970-01-01", restoredSig.getBirthdate());
        assertEquals("test", restoredSig.getImageData());
        assertEquals("image/png", restoredSig.getImageMimeType());
    }
    
    @Test
    public void failsIfNoEncryptorForVersion() {
        try {
            data.put("foo_code", "111");
            data.put("fooversion", 3);
            
            acct.getHealthId();
        } catch(BridgeServiceException e) {
            assertEquals("No encryptor can be found for version 3", e.getMessage());
        }
    }
    
    @Test
    public void phoneRetrievedWithNoVersion() {
        data.put("phone", "encrypted-2-555-555-5555");
        
        // This must use version 2, there's no version listed.
        String phone = acct.getAttribute("phone");
        assertEquals("555-555-5555", phone);
    }
    
    @Test
    public void phoneRetrievedWithCorrect() {
        data.put("phone", "encrypted-2-555-555-5555");
        data.put("phone_version", 2);
        
        // This must use version 2, there's no version listed.
        String phone = acct.getAttribute("phone");
        assertEquals("555-555-5555", phone);
    }
    
    @Test(expected = BridgeServiceException.class)
    public void phoneNotRetrievedWithIncorrectVersion() {
        data.put("phone", "encryptedphonenumber");
        data.put("phone_version", 3);
        
        acct.getAttribute("phone");
    }
    
    @Test
    public void retrievingNullEncryptedFieldReturnsNull() {
        String phone = acct.getAttribute("phone");
        assertNull(phone);
    }
    
    @Test
    public void canSetAndGetRoles() {
        acct.getRoles().add(DEVELOPER);
        
        assertEquals(1, acct.getRoles().size());
        assertEquals(DEVELOPER, acct.getRoles().iterator().next());
    }
}
