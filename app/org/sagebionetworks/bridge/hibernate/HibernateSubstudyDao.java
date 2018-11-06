package org.sagebionetworks.bridge.hibernate;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.dao.SubstudyDao;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.substudies.Substudy;
import org.sagebionetworks.bridge.models.substudies.SubstudyId;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableMap;

@Component
public class HibernateSubstudyDao implements SubstudyDao {
    private HibernateHelper hibernateHelper;
    
    @Resource(name = "substudyHibernateHelper")
    public final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }

    @Override
    public List<Substudy> getSubstudies(StudyIdentifier studyId, boolean includeDeleted) {
        checkNotNull(studyId);
        
        Map<String,Object> parameters = ImmutableMap.of("studyId", studyId.getIdentifier());
        String query = "from HibernateSubstudy as substudy where studyId=:studyId";
        
        return hibernateHelper.queryGet(query, parameters, 0, 1000, HibernateSubstudy.class)
                .stream().map((oneSubstudy) -> (Substudy) oneSubstudy).collect(Collectors.toList());
    }

    @Override
    public Substudy getSubstudy(StudyIdentifier studyId, String id) {
        checkNotNull(studyId);
        checkNotNull(id);

        Map<String,Object> parameters = ImmutableMap.of("studyId", studyId.getIdentifier(), "id", id);
        String query = "from HibernateSubstudy as substudy where studyId=:studyId and id=:id";
        
        List<HibernateSubstudy> list = hibernateHelper.queryGet(query, parameters, 0, 1, HibernateSubstudy.class);
        return (list.isEmpty()) ? null : list.get(0); 
    }
    
    @Override
    public VersionHolder createSubstudy(Substudy substudy) {
        checkNotNull(substudy);
        
        hibernateHelper.create(substudy);
        return new VersionHolder(substudy.getVersion());
    }

    @Override
    public VersionHolder updateSubstudy(Substudy substudy) {
        checkNotNull(substudy);
        
        hibernateHelper.update(substudy);
        return new VersionHolder(substudy.getVersion());
    }

    @Override
    public boolean deleteSubstudyPermanently(StudyIdentifier studyId, String id) {
        checkNotNull(studyId);
        checkNotNull(id);
        
        HibernateSubstudy substudy = (HibernateSubstudy)getSubstudy(studyId, id);
        if (substudy != null) {
            hibernateHelper.delete(HibernateSubstudy.class, substudy);
            return true;
        }
        return false;
    }
}
