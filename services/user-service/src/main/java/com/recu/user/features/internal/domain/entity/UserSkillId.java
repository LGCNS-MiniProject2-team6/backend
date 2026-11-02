package com.recu.user.features.internal.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import lombok.Getter;

@Embeddable
@Getter
public class UserSkillId implements Serializable {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "tech_tag_id")
    private Long techTagId;

    protected UserSkillId() {}

    public UserSkillId(Long userId, Long techTagId) {
        this.userId = userId;
        this.techTagId = techTagId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof UserSkillId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(techTagId, that.techTagId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, techTagId);
    }
}
