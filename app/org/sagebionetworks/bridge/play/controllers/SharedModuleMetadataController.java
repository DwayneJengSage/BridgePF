package org.sagebionetworks.bridge.play.controllers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;

import play.mvc.Result;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.sharedmodules.SharedModuleMetadata;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.SharedModuleMetadataService;

/** Play controller for Shared Module Metadata. */
@Controller
public class SharedModuleMetadataController extends BaseController {
    private SharedModuleMetadataService metadataService;

    /** Shared Module Metadata Service, configured by Spring. */
    @Autowired
    public final void setMetadataService(SharedModuleMetadataService metadataService) {
        this.metadataService = metadataService;
    }

    /** Creates the specified metadata object. */
    public Result createMetadata() throws Exception {
        verifySharedDeveloperAccess();
        SharedModuleMetadata metadata = parseJson(request(), SharedModuleMetadata.class);
        SharedModuleMetadata createdMetadata = metadataService.createMetadata(metadata);
        return createdResult(createdMetadata);
    }

    /** Deletes all metadata for module versions with the given ID. */
    public Result deleteMetadataByIdAllVersions(String id, String physical) {
        UserSession session = verifySharedDeveloperOrAdminAccess();
        
        if ("true".equals(physical) && session.isInRole(Roles.ADMIN)) {
            metadataService.deleteMetadataByIdAllVersionsPermanently(id);    
        } else {
            metadataService.deleteMetadataByIdAllVersions(id);
        }
        return okResult("Metadata has been deleted.");
    }

    /** Deletes metadata for the specified module ID and version. */
    public Result deleteMetadataByIdAndVersion(String id, int version, String physical) {
        UserSession session = verifySharedDeveloperOrAdminAccess();
        
        if ("true".equals(physical) && session.isInRole(Roles.ADMIN)) {
            metadataService.deleteMetadataByIdAndVersionPermanently(id, version);    
        } else {
            metadataService.deleteMetadataByIdAndVersion(id, version);
        }
        return okResult("Metadata has been deleted.");
    }

    /** Gets metadata for the specified version of the specified module. */
    public Result getMetadataByIdAndVersion(String id, int version) {
        SharedModuleMetadata metadata = metadataService.getMetadataByIdAndVersion(id, version);
        return okResult(metadata);
    }

    /** Gets metadata for the latest version of the specified module. */
    public Result getMetadataByIdLatestVersion(String id) {
        SharedModuleMetadata metadata = metadataService.getMetadataByIdLatestVersion(id);
        return okResult(metadata);
    }

    private String createQuery(String name, String notes, Map<String,Object> parameters) {
        List<String> clauses = new ArrayList<>();
        if (StringUtils.isNotBlank(name)) {
            clauses.add("name like :name");
            parameters.put("name", "%"+name+"%");
        }
        if (StringUtils.isNotBlank(notes)) {
            clauses.add("notes like :notes");
            parameters.put("notes", "%"+notes+"%");
        }
        return Joiner.on(" or ").join(clauses);
    }
    
    /**
     * Queries module metadata using the set of given parameters. See
     * {@link SharedModuleMetadataService#queryAllMetadata} for details. This method does not
     * require authentication.
     */
    public Result queryAllMetadata(String mostRecentString, String publishedString, String name, String notes,
            String tagsString, String includeDeleted) {
        // Parse inputs
        boolean mostRecent = Boolean.parseBoolean(mostRecentString);
        boolean published = Boolean.parseBoolean(publishedString);
        Set<String> tagSet = parseTags(tagsString);

        Map<String,Object> parameters = Maps.newHashMap();
        String where = createQuery(name, notes, parameters);
        
        // Call service
        List<SharedModuleMetadata> metadataList = metadataService.queryAllMetadata(mostRecent, published, where,
                parameters, tagSet, Boolean.valueOf(includeDeleted));
        return okResult(metadataList);
    }

    /** Similar to queryAllMetadata, except this only queries on module versions of the specified ID. */
    public Result queryMetadataById(String id, String mostRecentString, String publishedString, String name,
            String notes, String tagsString, String includeDeleted) {
        // Parse inputs
        boolean mostRecent = Boolean.parseBoolean(mostRecentString);
        boolean published = Boolean.parseBoolean(publishedString);
        Set<String> tagSet = parseTags(tagsString);

        Map<String,Object> parameters = Maps.newHashMap();
        String where = createQuery(name, notes, parameters);
        
        // Call service
        List<SharedModuleMetadata> metadataList = metadataService.queryMetadataById(id, mostRecent, published, where,
                parameters, tagSet, Boolean.valueOf(includeDeleted));
        return okResult(metadataList);
    }

    // Helper method to parse tags from URL query params. Package-scoped for unit tests.
    static Set<String> parseTags(String tagsString) {
        // Parse set of tags from a comma-delimited list.
        Set<String> tagSet = new HashSet<>();
        if (StringUtils.isNotBlank(tagsString)) {
            String[] tagsSplit = tagsString.split(",");
            Collections.addAll(tagSet, tagsSplit);
        }
        return tagSet;
    }

    /** Updates metadata for the specified module version. */
    public Result updateMetadata(String id, int version) {
        verifySharedDeveloperAccess();
        SharedModuleMetadata metadata = parseJson(request(), SharedModuleMetadata.class);
        SharedModuleMetadata updatedMetadata = metadataService.updateMetadata(id, version, metadata);
        return okResult(updatedMetadata);
    }

    // Helper method to verify caller permissions for write operations. You need to be a developer in the "shared"
    // study (the study for the Shared Module Library).
    private UserSession verifySharedDeveloperAccess() {
        UserSession session = getAuthenticatedSession(Roles.DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        if (!BridgeConstants.SHARED_STUDY_ID_STRING.equals(studyId.getIdentifier())) {
            throw new UnauthorizedException();
        }
        return session;
    }
    
    private UserSession verifySharedDeveloperOrAdminAccess() {
        UserSession session = getAuthenticatedSession(Roles.DEVELOPER, Roles.ADMIN);
        StudyIdentifier studyId = session.getStudyIdentifier();
        if (!BridgeConstants.SHARED_STUDY_ID_STRING.equals(studyId.getIdentifier())) {
            throw new UnauthorizedException();
        }
        return session;
    }
    
}
