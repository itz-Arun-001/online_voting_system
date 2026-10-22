package com.example.voting;

import com.example.voting.util.EmailUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmailUtilTest {
    @Test
    void normalizesCaseAndSpaces() {
        assertEquals("aarav@yourcollege.edu", EmailUtil.normalize("  Aarav@YourCollege.EDU "));
    }

    @Test
    void domainCheckIsCaseInsensitiveAfterNormalizing() {
        assertTrue(EmailUtil.hasDomain(EmailUtil.normalize("STUDENT@YOURCOLLEGE.EDU"), "YourCollege.edu"));
        assertTrue(EmailUtil.hasDomain("a@yourcollege.edu", "@yourcollege.edu"));
        assertFalse(EmailUtil.hasDomain("a@gmail.com", "yourcollege.edu"));
        assertFalse(EmailUtil.hasDomain("a@evilyourcollege.edu", "yourcollege.edu"));
    }

    @Test
    void rejectsBadFormats() {
        assertFalse(EmailUtil.isValid("not-an-email"));
        assertFalse(EmailUtil.isValid("a b@yourcollege.edu"));
        assertTrue(EmailUtil.isValid("a.b@yourcollege.edu"));
    }
}
