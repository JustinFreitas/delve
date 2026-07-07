package dev.freitas.delve.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class WebPropsTest {

    @Test
    void emptyAllowlistPermitsEveryone() {
        WebProps props = new WebProps();
        assertThat(props.isAllowed("123")).isTrue();
        assertThat(props.isAllowed("anyone")).isTrue();
    }

    @Test
    void populatedAllowlistRestricts() {
        WebProps props = new WebProps();
        props.setAllowedUserIds(List.of("111", "222"));
        assertThat(props.isAllowed("111")).isTrue();
        assertThat(props.isAllowed("222")).isTrue();
        assertThat(props.isAllowed("333")).isFalse();
    }

    @Test
    void nullAllowlistTreatedAsOpen() {
        WebProps props = new WebProps();
        props.setAllowedUserIds(null);
        assertThat(props.isAllowed("123")).isTrue();
    }

    @Test
    void adminUserIdsConfigurationWorks() {
        WebProps props = new WebProps();
        props.setAdminUserIds(List.of("999", "888"));
        assertThat(props.isAdmin("999")).isTrue();
        assertThat(props.isAdmin("888")).isTrue();
        assertThat(props.isAdmin("111")).isFalse();
    }
}
