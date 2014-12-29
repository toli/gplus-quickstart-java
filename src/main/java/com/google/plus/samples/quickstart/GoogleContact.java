package com.google.plus.samples.quickstart;

import com.google.gdata.data.contacts.ContactEntry;
import com.google.gdata.data.extensions.Email;

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

    Pattern ignoreEmailPattern = Pattern.compile(".*auto_reply.*|.*@sale.craigslist.org|sale-.*@craigslist.org|hous-.*@craigslist.org|"+
            ".*@reply.facebook.com|.*@reply.linkedin.com|.*@reply.airbnb.com|.*@guest.airbnb.com|.*@serv.craigslist.org|.*@groups.facebook.com|"+
            ".*@plus.google.com|.*@host.airbnb.com|.*@reply.craigslist.org|unsubscribe@.*|serv-.*@craigslist.org|"+
            ".*@docs.google.com|noreply.*@quip.com");

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


    public String getPrimaryEmail() {
        return primaryEmail;
    }

    public boolean hadIgnoredEmails() {
        return hadIgnoredEmails;
    }

    public void merge(GoogleContact inContact) {
        if((inContact.getFullName() != null) && (fullName == null)) {
            fullName = inContact.getFullName();
        }
        if(inContact.getEmails() != null) {
            emails.addAll(inContact.getEmails());
            if(primaryEmail  == null) {
                primaryEmail = inContact.getPrimaryEmail();
            }
        } 
    }

    @Override
    public String toString() {
        String theName = fullName == null ? "[no name]" : fullName;
        return theName + " [" + primaryEmail + "]" + ", " + Arrays.toString(emails.toArray());
    }
}
