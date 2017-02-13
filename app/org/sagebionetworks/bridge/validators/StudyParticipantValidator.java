package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.Set;

import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;

public class StudyParticipantValidator implements Validator {

    private final EmailValidator emailValidator = EmailValidator.getInstance();
    private final Study study;
    private final boolean isNew;
    
    public StudyParticipantValidator(Study study, boolean isNew) {
        this.study = study;
        this.isNew = isNew;
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return StudyParticipant.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        StudyParticipant participant = (StudyParticipant)object;
        
        if (isNew) {
            if (isBlank(participant.getEmail())) {
                errors.rejectValue("email", "is required");
            } else if (!emailValidator.isValid(participant.getEmail())){
                errors.rejectValue("email", "must be a valid email address");
            }
        } else {
            if (isBlank(participant.getId())) {
                errors.rejectValue("id", "is required");
            }
        }
        if (isNew && study.isExternalIdRequiredOnSignup() && isBlank(participant.getExternalId())) {
            errors.rejectValue("externalId", "is required");
        }
        // if external ID validation is enabled, it's not covered by the validator.
        for (String dataGroup : participant.getDataGroups()) {
            if (!study.getDataGroups().contains(dataGroup)) {
                errors.rejectValue("dataGroups", messageForSet(study.getDataGroups(), dataGroup));
            }
        }
        for (String attributeName : participant.getAttributes().keySet()) {
            if (!study.getUserProfileAttributes().contains(attributeName)) {
                errors.rejectValue("attributes", messageForSet(study.getUserProfileAttributes(), attributeName));
            }
        }
        if (isNew) {
            // This validation logic is also performed for reset password requests.
            String password = participant.getPassword();
            PasswordPolicy passwordPolicy = study.getPasswordPolicy();
            ValidatorUtils.validatePassword(errors, passwordPolicy, password);
        }
    }
    
    private String messageForSet(Set<String> set, String fieldName) {
        return String.format("'%s' is not defined for study (use %s)", 
                fieldName, BridgeUtils.COMMA_SPACE_JOINER.join(set));
    }
}
