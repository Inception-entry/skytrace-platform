package com.skytrace.backend.evidence.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "evidence_tag_rel")
@IdClass(EvidenceTagRel.PK.class)
public class EvidenceTagRel {

    @Id
    @Column(name = "evidence_id")
    private Long evidenceId;

    @Id
    @Column(name = "tag_id")
    private Long tagId;

    public EvidenceTagRel() {
    }

    public EvidenceTagRel(Long evidenceId, Long tagId) {
        this.evidenceId = evidenceId;
        this.tagId = tagId;
    }

    public Long getEvidenceId() {
        return evidenceId;
    }

    public Long getTagId() {
        return tagId;
    }

    public static class PK implements Serializable {
        private Long evidenceId;
        private Long tagId;

        public PK() {
        }

        public PK(Long evidenceId, Long tagId) {
            this.evidenceId = evidenceId;
            this.tagId = tagId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PK pk)) {
                return false;
            }
            return Objects.equals(evidenceId, pk.evidenceId)
                    && Objects.equals(tagId, pk.tagId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(evidenceId, tagId);
        }
    }
}
