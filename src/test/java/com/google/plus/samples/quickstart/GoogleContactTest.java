package com.google.plus.samples.quickstart;

import com.google.gdata.data.contacts.ContactEntry;
import com.google.gdata.data.extensions.Email;
import com.google.gdata.data.extensions.FullName;
import com.google.gdata.data.extensions.Name;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author toli kuznets
 * @version $Id$
 */


public class GoogleContactTest {
    @BeforeClass
    public static void beforeClass() {
        Signin.loadSpringContext();
    }

    @Test
    public void testMerge() throws Exception {
        GoogleContact c1 = new GoogleContact(createEntry("vasya pupkin", Arrays.asList("vasya@pupkin.com")));
        GoogleContact c2 = new GoogleContact(createEntry(null, Arrays.asList("head@pupkin.com")));

        c1.merge(c2);
        assertEquals("full name", "vasya pupkin", c1.getFullName());
        assertEquals("primary email should stay form original contact", "vasya@pupkin.com", c1.getPrimaryEmail());
        assertEquals("num emails", 2, c1.getEmails().size());
    }

    @Test
    /**
     * blabla@sale.craigslist.com should not be added
     */
    public void testIgnoredEmails() throws Exception {
        GoogleContact contact = new GoogleContact(createEntry("vasya pupkin", Arrays.asList("vasya@pupkin.com", "auto_reply-2134@pupkin.com")));
        assertEquals("only 1 email", 1, contact.getEmails().size());
        assertEquals("vasya@pupkin.com", contact.getEmails().toArray(new String[1])[0]);

        contact = new GoogleContact(createEntry("vasya pupkin", Arrays.asList("auto_reply-2134@sale.craigslist.org", "vasya@pupkin.com")));
        assertEquals("only 1 email", 1, contact.getEmails().size());
        assertEquals("vasya@pupkin.com", contact.getEmails().toArray(new String[1])[0]);

        contact = new GoogleContact(createEntry("vasya pupkin", Arrays.asList("sale-jgmmg-1746422063@craigslist.org", "vasya@pupkin.com")));
        assertEquals("only 1 email", 1, contact.getEmails().size());
        assertEquals("vasya@pupkin.com", contact.getEmails().toArray(new String[1])[0]);

        contact = new GoogleContact(createEntry("vasya pupkin", Arrays.asList("4zjqf-3392859377@sale.craigslist.org", "vasya@pupkin.com")));
        assertEquals("only 1 email", 1, contact.getEmails().size());
        assertEquals("vasya@pupkin.com", contact.getEmails().toArray(new String[1])[0]);
    }

    @Test
    /**
     * Creates a contact without a name and merges it with a contact with a name and another email.
     * Verify we update the mergedNames list
     */
    public void testGetMerged() throws Exception {
        GoogleContact contact1 = new GoogleContact(createEntry(null, Arrays.asList("vasya@pupkin.com")));
        GoogleContact contact2 = new GoogleContact(createEntry("vasya pupkin", Arrays.asList("head@pupkin.com")));
        contact1.merge(contact2);
        String[] mergedNames = GoogleContact.getMergedNames();
        assertEquals("expecting size 1", 1, mergedNames.length);
    }

    private ContactEntry createEntry(String inName, List<String> emails){
        ContactEntry entry = new ContactEntry();
        Name name = new Name();
        FullName fname = new FullName();
        fname.setValue(inName);
        name.setFullName(fname);
        entry.setName(name);
        for (int i=0;i<emails.size();i++) {
            Email g_email = new Email();
            g_email.setAddress(emails.get(i));
            if(i==0) {
                g_email.setPrimary(true);
            }
            entry.addEmailAddress(g_email);
        }
        return entry;
    }
}
