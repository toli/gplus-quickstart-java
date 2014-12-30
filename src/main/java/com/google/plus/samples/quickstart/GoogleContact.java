package com.google.plus.samples.quickstart;

import com.google.gdata.data.contacts.ContactEntry;
import com.google.gdata.data.extensions.Email;
import org.apache.commons.lang.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author toli kuznets
 * @version $Id$
 */


public class GoogleContact {
    private String fullName;
    private String primaryEmail;
    private Set<String> emails = new HashSet<String>();
    private boolean hadIgnoredEmails = false;
    private static Pattern ignoreEmailPattern;
    private static int numMerged =0;

    @SuppressWarnings("unused") // Spring setter
    public static void setIgnoredPatterns(List<String> inIgnoredPatterns) {
        ignoreEmailPattern = Pattern.compile(StringUtils.join(inIgnoredPatterns.toArray(new String[inIgnoredPatterns.size()]), "|"));
    }

    public GoogleContact(ContactEntry entry) {
        List<Email> emailAddresses = entry.getEmailAddresses();
        for (Email emailAddress : emailAddresses) {
            String oneEmail = emailAddress.getAddress();
            if(!ignoreEmailPattern.matcher(oneEmail).matches()) {
                emails.add(oneEmail);
                if (emailAddress.getPrimary()) {
                    primaryEmail = oneEmail;
                }
            } else {
                hadIgnoredEmails = true;
            }
        }
        fullName = (entry.getName() != null) ? entry.getName().getFullName().getValue() : null;
    }

    public String getFullName() {
        return fullName;
    }

    public Set<String> getEmails() {
        return emails;
    }

    public static int getNumMerged() { return numMerged;}

    public String getPrimaryEmail() {
        return primaryEmail;
    }

    public boolean hadIgnoredEmails() {
        return hadIgnoredEmails;
    }

    public void merge(GoogleContact inContact) {
        boolean merged = false;
        if((inContact.getFullName() != null) && (fullName == null)) {
            fullName = inContact.getFullName();
            merged = true;
        }
        if(inContact.getEmails() != null) {
            emails.addAll(inContact.getEmails());
            if(primaryEmail  == null) {
                primaryEmail = inContact.getPrimaryEmail();
                merged = true;
            }
        }
        if(merged) {
            numMerged++;
        }
    }

    @Override
    public String toString() {
        String theName = fullName == null ? "[no name]" : fullName;
        return theName + " [" + primaryEmail + "]" + ", " + Arrays.toString(emails.toArray());
    }
}
