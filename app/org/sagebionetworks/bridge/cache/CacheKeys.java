package org.sagebionetworks.bridge.cache;

import java.util.List;
import java.util.Objects;

import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

/**
 * A utility to provide consistency, centralization, and type safety for the keys we use 
 * to store data in Redis. This makes it easier to determine which keys can be returned as 
 * part of the administrative API so we can keep PII data out of the cache keys we return.
 */
public class CacheKeys {
    
    private static final Joiner COLON_JOINER = Joiner.on(":");
    private static final String[] PRIVATE_KEYS = new String[] {
            "session", "user", "request-info", "signInRequest", "phoneSignInRequest", "itp"};
    
    public static boolean isPublic(String key) {
        for (String suffix : PRIVATE_KEYS) {
            if (key.endsWith(":"+suffix)) {
                return false;
            }
        }
        return true;
    }
    
    public static class CacheKey {
        private final String key;
        private CacheKey(String... elements) {
            this.key = COLON_JOINER.join(elements);
        }
        @Override
        public String toString() {
            return key;
        }
        @Override
        public int hashCode() {
            return key.hashCode();
        }
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null || getClass() != obj.getClass())
                return false;
            CacheKey other = (CacheKey) obj;
            return Objects.equals(key, other.key);
        }
    }
    
    public static final CacheKey appConfigList(String studyId) {
        return new CacheKey(studyId, "AppConfigList");
    }
    public static final CacheKey channelThrottling(String throttleType, String userId) {
        return new CacheKey(userId, throttleType, "channel-throttling");
    }
    public static final CacheKey emailSignInRequest(SignIn signIn) {
        return new CacheKey(signIn.getEmail(), signIn.getStudyId(), "signInRequest");
    }
    /** The email verification status from Amazon SES, which we cache for a short time. Not involved with 
     * verification of an individual's email address. So we do return it through the cache API.
     */
    public static final CacheKey emailVerification(String email) {
        return new CacheKey(email, "emailVerificationStatus");
    }
    public static final CacheKey itp(SubpopulationGuid subpopGuid, StudyIdentifier studyId, Phone phone) {
        return new CacheKey(subpopGuid.getGuid(), phone.getNumber(), studyId.getIdentifier(), "itp");
    }
    public static final CacheKey lock(String value, Class<?> clazz) {
        return new CacheKey(value, clazz.getCanonicalName(), "lock");
    }
    public static final CacheKey passwordResetForEmail(String sptoken, String studyId) {
        return new CacheKey(sptoken, studyId); // no type, not great
    }
    public static final CacheKey passwordResetForPhone(String sptoken, String studyId) { 
        return new CacheKey(sptoken, "phone", studyId); // no type, not great
    }
    public static final CacheKey phoneSignInRequest(SignIn signIn) {
        return new CacheKey(signIn.getPhone().getNumber(), signIn.getStudyId(),"phoneSignInRequest");
    }
    public static final CacheKey requestInfo(String userId) {
        return new CacheKey(userId, "request-info");
    }
    public static final CacheKey sessionKey(String sessionToken) {
        return new CacheKey(sessionToken, "session");
    }
    public static final CacheKey study(String studyId) {
        return new CacheKey(studyId, "study");
    }    
    public static final CacheKey subpop(SubpopulationGuid subpopGuid, StudyIdentifier studyId) {
        return new CacheKey(subpopGuid.getGuid(), studyId.getIdentifier(), "Subpopulation");
    }
    public static final CacheKey subpopList(StudyIdentifier studyId) {
        return new CacheKey(studyId.getIdentifier(), "SubpopulationList");
    }
    public static final CacheKey userSessionKey(String userId) {
        return new CacheKey(userId, "session", "user");
    }
    public static final CacheKey verificationToken(String sptoken) {
        return new CacheKey(sptoken); // no type, not great
    }
    public static final CacheKey viewKey(Class<?> clazz, String... elements) {
        List<String> list = Lists.newArrayList(elements);
        list.add(clazz.getSimpleName());
        list.add("view");
        return new CacheKey(COLON_JOINER.join(list));
    }
}
