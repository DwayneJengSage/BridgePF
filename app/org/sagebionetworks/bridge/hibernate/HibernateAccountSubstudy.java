package org.sagebionetworks.bridge.hibernate;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;

import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;
import org.sagebionetworks.bridge.models.substudies.AccountSubstudyId;

@Entity
@Table(name = "AccountsSubstudies")
@IdClass(AccountSubstudyId.class)
public class HibernateAccountSubstudy implements AccountSubstudy {

    @Id
    private String studyId;
    @Id
    private String substudyId;
    @Id
    private String accountId;
    private String externalId;
    
    public HibernateAccountSubstudy() {
    }
    
    public HibernateAccountSubstudy(String studyId, String substudyId, String accountId) {
        this.studyId = studyId;
        this.substudyId = substudyId;
        this.accountId = accountId;
    }
    
    public String getStudyId() {
        return studyId;
    }
    /*
    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }
    */
    public String getSubstudyId() {
        return substudyId;
    }
    /*
    public void setSubstudyId(String substudyId) {
        this.substudyId = substudyId;
    }*/
    public String getAccountId() {
        return accountId;
    }
    /*
    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }*/
    public String getExternalId() {
        return externalId;
    }
    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }
}
