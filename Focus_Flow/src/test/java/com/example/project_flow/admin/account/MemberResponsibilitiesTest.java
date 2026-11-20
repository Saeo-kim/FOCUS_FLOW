package com.example.project_flow.admin.account;

import com.example.project_flow.admin.account.AccountDtos.MemberCreateRequest;
import com.example.project_flow.admin.account.AccountDtos.MemberUpdateRequest;
import com.example.project_flow.admin.account.AccountDtos.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
class MemberResponsibilitiesTest {

    @Autowired AccountService accounts;

    @Test
    void responsibilitiesAreSavedOnInviteKeptWhenNotSentAndClearedWhenEmptied() {
        String username = "resp-" + UUID.randomUUID().toString().substring(0, 8);
        String duties = "  Maintain project documentation.\nTrack bugs and proposed improvements.  ";

        UserResponse created = accounts.invite(new MemberCreateRequest(username, "Knowledge Manager", null,
                UserRole.MEMBER, AccountKind.HUMAN, "Software Development Knowledge Manager", Department.ENGINEERING, duties)).user();
        assertEquals("Maintain project documentation.\nTrack bugs and proposed improvements.", created.responsibilities());

        // An edit that does not send responsibilities (null) leaves them unchanged.
        UserResponse kept = accounts.update(-1L, created.id(), new MemberUpdateRequest("Knowledge Manager", null, null,
                AccountKind.HUMAN, "Software Development Knowledge Manager", Department.ENGINEERING, null));
        assertEquals("Maintain project documentation.\nTrack bugs and proposed improvements.", kept.responsibilities());

        // An empty field clears them.
        UserResponse cleared = accounts.update(-1L, created.id(), new MemberUpdateRequest("Knowledge Manager", null, null,
                AccountKind.HUMAN, "Software Development Knowledge Manager", Department.ENGINEERING, "   "));
        assertNull(cleared.responsibilities());
    }
}
