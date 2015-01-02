/*
 * Copyright 2013 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.plus.samples.quickstart;

import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.googleapis.auth.oauth2.*;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.gdata.client.Query;
import com.google.gdata.client.contacts.ContactsService;
import com.google.gdata.data.contacts.ContactEntry;
import com.google.gdata.data.contacts.ContactFeed;
import com.google.gdata.util.ServiceException;
import com.google.gson.Gson;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.ServletHandler;
import org.mortbay.jetty.servlet.SessionHandler;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.math.BigInteger;
import java.net.URL;
import java.security.SecureRandom;
import java.util.*;

/**
 * Simple server to demonstrate how to use Google+ Sign-In and make a request
 * via your own server.
 *
 * @author joannasmith@google.com (Joanna Smith)
 * @author vicfryzel@google.com (Vic Fryzel)
 */
public class Signin {
    private static final Logger LOGGER = Logger.getLogger(Signin.class);
    /*
     * Default HTTP transport to use to make HTTP requests.
     */
    private static final HttpTransport TRANSPORT = new NetHttpTransport();

    /*
     * Default JSON factory to use to deserialize JSON.
     */
    private static final JacksonFactory JSON_FACTORY = new JacksonFactory();

    /*
     * Gson object to serialize JSON responses to requests to this servlet.
     */
    private static final Gson GSON = new Gson();

    /*
     * Creates a client secrets object from the client_secrets.json file.
     */
    private static GoogleClientSecrets clientSecrets;

    static {
        try {
            Reader reader = new FileReader("client_secrets.json");
            clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, reader);
        } catch (IOException e) {
            throw new Error("No client_secrets.json found", e);
        }
    }

    /*
     * This is the Client ID that you generated in the API Console.
     */
    private static final String CLIENT_ID = clientSecrets.getWeb().getClientId();

    /*
     * This is the Client Secret that you generated in the API Console.
     */
    private static final String CLIENT_SECRET = clientSecrets.getWeb().getClientSecret();

    /*
     * Optionally replace this with your application's name.
     */
    private static final String APPLICATION_NAME = "Contacts Deduper";

    /**
     * Register all endpoints that we'll handle in our server.
     *
     * @param args Command-line arguments.
     * @throws Exception from Jetty if the component fails to start
     */
    @SuppressWarnings("unused")
    public static void main(String[] args) throws Exception {
        PropertyConfigurator.configure("log4j.properties");

        loadSpringContext();

        Server server = new Server(4567);
        ServletHandler servletHandler = new ServletHandler();
        SessionHandler sessionHandler = new SessionHandler();
        sessionHandler.setHandler(servletHandler);
        server.setHandler(sessionHandler);
        servletHandler.addServletWithMapping(ConnectServlet.class, "/connect");
        servletHandler.addServletWithMapping(DisconnectServlet.class, "/disconnect");
        servletHandler.addServletWithMapping(PeopleServlet.class, "/people");
        servletHandler.addServletWithMapping(MainServlet.class, "/");
        server.start();
        server.join();
    }

    protected static void loadSpringContext() {
        new ClassPathXmlApplicationContext("ignore-list.xml");
    }

    /**
     * Initialize a session for the current user, and render index.html.
     */
    public static class MainServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            // This check prevents the "/" handler from handling all requests by default
            if (!"/".equals(request.getServletPath())) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            response.setContentType("text/html");
            try {
                // Create a state token to prevent request forgery.
                // Store it in the session for later validation.
                String state = new BigInteger(130, new SecureRandom()).toString(32);
                request.getSession().setAttribute("state", state);
                // Fancy way to read index.html into memory, and set the client ID
                // and state values in the HTML before serving it.
                response.getWriter().print(new Scanner(new File("index.html"), "UTF-8")
                //response.getWriter().print(new Scanner(new File("deduper.html"), "UTF-8")
                        .useDelimiter("\\A").next()
                        .replaceAll("[{]{2}\\s*CLIENT_ID\\s*[}]{2}", CLIENT_ID)
                        .replaceAll("[{]{2}\\s*STATE\\s*[}]{2}", state)
                        .replaceAll("[{]{2}\\s*APPLICATION_NAME\\s*[}]{2}",
                                APPLICATION_NAME));
                response.setStatus(HttpServletResponse.SC_OK);
            } catch (FileNotFoundException e) {
                // When running the quickstart, there was some path issue in finding
                // index.html.  Double check the quickstart guide.
                e.printStackTrace();
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().print(e.toString());
            }
        }
    }

    /**
     * Upgrade given auth code to token, and store it in the session.
     * POST body of request should be the authorization code.
     * Example URI: /connect?state=...&gplus_id=...
     */
    public static class ConnectServlet extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            response.setContentType("application/json");

            // Only connect a user that is not already connected.
            String tokenData = (String) request.getSession().getAttribute("token");
            if (tokenData != null) {
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().print(GSON.toJson("Current user is already connected."));
                return;
            }
            // Ensure that this is no request forgery going on, and that the user
            // sending us this connect request is the user that was supposed to.
            if (!request.getParameter("state").equals(request.getSession().getAttribute("state"))) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().print(GSON.toJson("Invalid state parameter."));
                return;
            }
            // Normally the state would be a one-time use token, however in our
            // simple case, we want a user to be able to connect and disconnect
            // without reloading the page.  Thus, for demonstration, we don't
            // implement this best practice.
            //request.getSession().removeAttribute("state");

            ByteArrayOutputStream resultStream = new ByteArrayOutputStream();
            getContent(request.getInputStream(), resultStream);
            String code = new String(resultStream.toByteArray(), "UTF-8");

            try {
                // Upgrade the authorization code into an access and refresh token.
                GoogleTokenResponse tokenResponse =
                        new GoogleAuthorizationCodeTokenRequest(TRANSPORT, JSON_FACTORY,
                                CLIENT_ID, CLIENT_SECRET, code, "postmessage").execute();

                // Store the token in the session for later use.
                request.getSession().setAttribute("token", tokenResponse.toString());
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().print(GSON.toJson("Successfully connected user."));
            } catch (TokenResponseException e) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().print(GSON.toJson("Failed to upgrade the authorization code."));
            } catch (IOException e) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().print(GSON.toJson("Failed to read token data from Google. " +
                        e.getMessage()));
            }
        }

        /*
         * Read the content of an InputStream.
         *
         * @param inputStream the InputStream to be read.
         * @return the content of the InputStream as a ByteArrayOutputStream.
         * @throws IOException
         */
        static void getContent(InputStream inputStream, ByteArrayOutputStream outputStream)
                throws IOException {
            // Read the response into a buffered stream
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            int readChar;
            while ((readChar = reader.read()) != -1) {
                outputStream.write(readChar);
            }
            reader.close();
        }
    }

    /**
     * Revoke current user's token and reset their session.
     */
    public static class DisconnectServlet extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            response.setContentType("application/json");

            // Only disconnect a connected user.
            String tokenData = (String) request.getSession().getAttribute("token");
            if (tokenData == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().print(GSON.toJson("Current user not connected."));
                return;
            }
            try {
                // Build credential from stored token data.
                GoogleCredential credential = new GoogleCredential.Builder()
                        .setJsonFactory(JSON_FACTORY)
                        .setTransport(TRANSPORT)
                        .setClientSecrets(CLIENT_ID, CLIENT_SECRET).build()
                        .setFromTokenResponse(JSON_FACTORY.fromString(
                                tokenData, GoogleTokenResponse.class));
                // Execute HTTP GET request to revoke current token.
                TRANSPORT.createRequestFactory()
                        .buildGetRequest(new GenericUrl(
                                String.format(
                                        "https://accounts.google.com/o/oauth2/revoke?token=%s",
                                        credential.getAccessToken()))).execute();
                // Reset the user's session.
                request.getSession().removeAttribute("token");
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().print(GSON.toJson("Successfully disconnected."));
            } catch (IOException e) {
                // For whatever reason, the given token was invalid.
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().print(GSON.toJson("Failed to revoke token for given user."));
            }
        }
    }

    /**
     * Get list of people user has shared with this app.
     */
    public static class PeopleServlet extends HttpServlet {
        private static final int REQUEST_WINDOW = 5000;

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            response.setContentType("application/json");

            // Only fetch a list of people for connected users.
            String tokenData = (String) request.getSession().getAttribute("token");
            if (tokenData == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().print(GSON.toJson("Current user not connected."));
                return;
            }
            try {
                // Build credential from stored token data.
                GoogleCredential credential = new GoogleCredential.Builder()
                        .setJsonFactory(JSON_FACTORY)
                        .setTransport(TRANSPORT)
                        .setClientSecrets(CLIENT_ID, CLIENT_SECRET).build()
                        .setFromTokenResponse(JSON_FACTORY.fromString(
                                tokenData, GoogleTokenResponse.class));

                printAllContacts(credential, response);
            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().print(GSON.toJson("Failed to read data from Google. " + e.getMessage()));
                e.printStackTrace();
            }
        }

        /**
         * This function gets the Google credential and reaches out to Google to download all the contacts for the
         * specified person.
         * It then proceeds to dedupe the contacts - builds a few mapping tables
         * 1. fullNameToPerson - table of contact name to all of the contact emails
         * 2. emailToPerson - reverse map, of unique emails to Google Contacts
         * 3. noNameSetOfEmails - emails that dont' have names associated with them.
         *
         * As it reads the incoming contacts, it tries to see if a contact contains an email that's already been seen and merges
         * the incoming contact to an existing one in that case
         * If the contact has same (lowercase) name as existing contact, merges all emails to existing contact
         * (as a result, it's possible that we may have all lowercase full name if that's found first, can obviously
         * clean up and Camel Case names before sending out invites)
         * Cleans up the "orphaned no-name emails" if it encounters an email w/out an associated name
         * that's already been seen in some other known contact
         *
         * @param credential Google credential
         * @param response  servlet response
         * @throws ServiceException
         * @throws IOException
         */

        public static void printAllContacts(GoogleCredential credential, HttpServletResponse response)
                throws ServiceException, IOException {
            ContactsService contactsService = new ContactsService("Contacts-Lister");
            if (credential != null && (credential.getExpiresInSeconds() == null || credential.getExpiresInSeconds() < 5)) {
                credential.refreshToken();
            }

            contactsService.setOAuth2Credentials(credential);

            // Request the feed
            URL feedUrl = new URL("https://www.google.com/m8/feeds/contacts/default/full");
            Query query = new Query(feedUrl);
            query.setMaxResults(REQUEST_WINDOW);
            int totalContactsRead = 0;
            Map<String, GoogleContact> emailToPerson = new HashMap<String, GoogleContact>();
            Map<String, GoogleContact> fullNameToPerson = new HashMap<String, GoogleContact>();
            Map<String, GoogleContact> phoneToPerson = new HashMap<String, GoogleContact>();
            Set<String> noNameSetOfEmails = new HashSet<String>();
            String ownerName = "[contacts owner]";

            LOGGER.info("Connecting to Google to read contacts list, this may take a while");
            while (true) {
                ContactFeed resultFeed = contactsService.getFeed(query, ContactFeed.class);
                int numRead = resultFeed.getEntries().size();
                if (numRead == 0) {
                    System.out.println("reached end of feed");
                    break;
                }
                // Print the results
                ownerName = resultFeed.getTitle().getPlainText();
                System.out.println("reading the feed of: " + ownerName);
                System.out.println();
                for (ContactEntry entry : resultFeed.getEntries()) {
                    GoogleContact contact = new GoogleContact(entry);
                    totalContactsRead++;
                    LOGGER.trace(totalContactsRead + ": " + contact.toString());
                    if (!(contact.hadIgnoredEmails() && contact.getEmails().size() == 0)) {
                        // handle name -> contact mapping
                        if ((contact.getFullName() != null)) {
                            if (fullNameToPerson.containsKey(contact.getFullName().toLowerCase())) {
                                // merge existing to incoming
                                fullNameToPerson.get(contact.getFullName().toLowerCase()).merge(contact);
                            } else {
                                // add new one
                                fullNameToPerson.put(contact.getFullName().toLowerCase(), contact);

                            }
                            // if we found someone with a name, remove any of those emails, that may have been w/out a name
                            // that are also owned by this named person
                            CollectionUtils.subtract(noNameSetOfEmails, contact.getEmails());
                        } else {
                            // if there's no name, add all the emails to non-name set
                            noNameSetOfEmails.addAll(contact.getEmails());
                        }

                        boolean merged = updateEmailToContactsMap(contact, emailToPerson);
                        //merged |= updatePhoneToContactsMap(contact, phoneToPerson);
                        if (merged) {
                            noNameSetOfEmails.removeAll(contact.getEmails());
                            LOGGER.debug("Found a merge for " + contact.getEmails().toArray()[0] + ", removing from noName set");
                        }
                    }
                }
                query.setStartIndex(query.getStartIndex() + REQUEST_WINDOW);
                LOGGER.debug("reset query startIndex to " + query.getStartIndex());
            }
            String[] justNamesArr = pruneNoEmailContacts(fullNameToPerson);
            printByName(fullNameToPerson);
            printStringArray(noNameSetOfEmails.toArray(new String[noNameSetOfEmails.size()]), "no-name emails");
            printStringArray(justNamesArr, "just names, no emails");

            System.out.println("Read total of contacts: " + totalContactsRead);
            System.out.println("uniqueEmails total: " + emailToPerson.size());
            System.out.println("emails with no names: " + noNameSetOfEmails.size());
            System.out.println("just names w/out emails: " + justNamesArr.length);
            System.out.println("Contacts with full name and emails: " + fullNameToPerson.size());
            System.out.println("Merged " + GoogleContact.getMergedNames().length + " contacts: "
                    + Arrays.toString(GoogleContact.getMergedNames()));

            response.getWriter().print(GSON.toJson("Read total of " + totalContactsRead + " contacts\n"));
            File outfile = new File("deduped-output.html");
            response.getWriter().print(GSON.toJson("Toli sucks at Web output. Please open the generated file in a browser instead: " + outfile.getAbsolutePath()));
            constructOutputHtml(outfile, ownerName, totalContactsRead, fullNameToPerson, emailToPerson, noNameSetOfEmails, justNamesArr);
            response.setStatus(HttpServletResponse.SC_OK);
        }

        /**
         * constructs the HTML output for deduped results
         * I such at front-end, so this is a nice throwback to 1995
         */
        private static void constructOutputHtml(File outfile, String contactsOwner, int totalContactsRead,
                                                Map<String, GoogleContact> fullNameToPerson,
                                                Map<String, GoogleContact> emailToPerson,
                                                Set<String> noNameSetOfEmails, String[] justNamesArr) {

            try {
                FileUtils.write(outfile, ""); // erase the file if it's already present
                FileUtils.write(outfile, "<html>\n<body>\n", true);
                FileUtils.write(outfile, "<em>[" + contactsOwner + "]: total # of contacts read: " + totalContactsRead + "</em><br>\n", true);
                FileUtils.write(outfile, "<em>total unique emails: " + emailToPerson.size() + "</em><br/>\n", true);
                FileUtils.write(outfile, "<em>Emails with no names: " + noNameSetOfEmails.size() + "</em><br>\n", true);
                FileUtils.write(outfile, "<em>Just names with no emails: " + justNamesArr.length + "</em><br>\n", true);

                FileUtils.write(outfile, "<h3>All Deduped Contacts: " + fullNameToPerson.size() + "</h3><br/>\n", true);
                FileUtils.write(outfile, "<table border='1'>\n", true);
                FileUtils.write(outfile, "<th width='15%'>Name</th><th width='15%'>Primary email</th><th>All Emails</th>\n", true);
                String[] allNames = fullNameToPerson.keySet().toArray(new String[fullNameToPerson.size()]);
                Arrays.sort(allNames);
                for (String name : allNames) {
                    GoogleContact contact = fullNameToPerson.get(name);
                    FileUtils.write(outfile, "<tr><td>" + contact.getFullName() + "</td><td>" + contact.getPrimaryEmail() + "</td><td>" +
                            Arrays.toString(contact.getEmails().toArray()) + "</td></tr>\n", true);
                }
                FileUtils.write(outfile, "</table>\n", true);


                FileUtils.write(outfile, "<p/>", true);
                FileUtils.write(outfile, "<em>total unique emails: " + emailToPerson.size() + "</em><br>\n", true);
                FileUtils.write(outfile, "<h3>Emails with no names: " + noNameSetOfEmails.size() + "</h3><br>\n", true);
                String[] sortedNoNameEmails = noNameSetOfEmails.toArray(new String[noNameSetOfEmails.size()]);
                Arrays.sort(sortedNoNameEmails);
                for (String justEmail : sortedNoNameEmails) {
                    FileUtils.write(outfile, justEmail + "<br/>\n", true);
                }
                FileUtils.write(outfile, "<p/>\n", true);
                Arrays.sort(justNamesArr);
                FileUtils.write(outfile, "<h3>Just names with no emails: " + justNamesArr.length + "</h3><br>\n", true);
                for (String name : justNamesArr) {
                    FileUtils.write(outfile, name + "<br/>\n", true);
                }
                FileUtils.write(outfile, "<em>Merged " + GoogleContact.getMergedNames().length + " contacts: " +
                        Arrays.toString(GoogleContact.getMergedNames()) + "<br/>\n", true);
                FileUtils.write(outfile, "</body></html>\n", true);

            } catch (IOException ex) {
                System.out.println("Error writing out put file: " + ex);
            }
        }


        private static String[] pruneNoEmailContacts(Map<String, GoogleContact> fullNameToPerson) {
            ArrayList<String> justNames = new ArrayList<String>();
            GoogleContact[] allContacts = fullNameToPerson.values().toArray(new GoogleContact[fullNameToPerson.size()]);
            for (GoogleContact contact : allContacts) {
                if (contact.getEmails().size() == 0) {
                    fullNameToPerson.remove(contact.getFullName().toLowerCase());
                    justNames.add(contact.getFullName());
                }
            }
            String[] justNamesArr = justNames.toArray(new String[justNames.size()]);
            Arrays.sort(justNamesArr);
            return justNamesArr;
        }

        private static void printStringArray(String[] array, String header) {
            System.out.println("================ " + header + ": " + array.length + "==================");
            Arrays.sort(array);
            for (int i = 0; i < array.length; i++) {
                System.out.println(i + ": " + array[i]);
            }
            System.out.println();
        }

        /**
         * Prints by full name
         */
        private static void printByName(Map<String, GoogleContact> fullNameToPerson) {
            System.out.println("================ by name " + fullNameToPerson.size() + "==================");
            String[] allNames = fullNameToPerson.keySet().toArray(new String[fullNameToPerson.size()]);
            Arrays.sort(allNames);
            for (int i = 0; i < allNames.length; i++) {
                System.out.println(i + ": " + fullNameToPerson.get(allNames[i]));
            }
            System.out.println();
        }

        /**
         * add all the unique emails into the map
         *
         * @param contact
         * @param emailToPerson
         * @return Whether or not existing contact was found and this was merged into that - means you can avoid putting the secondary contact in
         */
        private static boolean updateEmailToContactsMap(GoogleContact contact, Map<String, GoogleContact> emailToPerson) {
            Set<String> allEmails = contact.getEmails();
            boolean merged = false;
            for (String oneEmail : allEmails) {
                if (emailToPerson.containsKey(oneEmail)) {
                    // merge
                    GoogleContact existing = emailToPerson.get(oneEmail);
                    existing.merge(contact);
                    emailToPerson.put(oneEmail, existing);
                    merged = true;
                } else {
                    emailToPerson.put(oneEmail, contact);
                }
            }
            return merged;
        }

        private static boolean updatePhoneToContactsMap(GoogleContact inContact, Map<String, GoogleContact> inMap) {
            // implement me
            return false;
        }
    }
}
