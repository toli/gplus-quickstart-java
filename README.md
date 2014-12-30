Originally the code was copied/forked from Google+ Java Quickstart
https://developers.google.com/+/quickstart/java

I have subsequently modified it heavily to get rid of Google+ and substitute handling for 
Google Contacts, and some first-pass at deduplication and specifying the list of emails to ignore.

You need to have Maven/Java installed, and run it with 
> mvn compile resources:resources exec:java

This will run an internal server at http://localhost:4567
If you navigate there, you will see a (poor excuse) for  webpage with Google signin-button. 
Sign-in, and another page will load. 
I have horribly failed at doing UI, so instead of redirecting or showing the output in browser directly, it will output the data to console and will also create a deduped-output.html file that you should opena and see all the deduped contacts. 

You will see some stats for read/unique contacts/emails, and will see a table of contacts, along with a list of "names without emails" and "emails without names".

In general, the following deduplications were in effect:
- full names were merged regardless of case (and the first contact "seen" would win future capitalization)
- emails that would show up under different contacts would lead to a "merge"
- all ignored emialsl are filtered, and if that left a contact w/out emails it'd be dropped.

Future work and cases that were not take care of:
UI - i have failed horribly. I'd need to learn about UIs/redirection/sessions management/etc. You currently may need to reload the page a few times for the login to go through, and then wait ~10secs for data to start coming back. 
I horribly suck at UI, and after sinking a (scarily) large number of hours into making it work i gave up and wrote out to a file instead.
- Deduplication of names with "suffixes" - sucha s "Bob Smith/Bob Smit - (bla bla)" would be smart. 
Examples:
1319: Mark Chamberlain [bigkop@googlemail.com], [bigkop@googlemail.com, mark@createnetworks.co.uk]
1320: Mark Chamberlain - Sun UK [mark.chamberlain@sun.com], [mark.chamberlain@sun.com]
- extracting person's name from email in case we don't have "real email". 
example: Eugenia.Volen@ey.com [eugenia.volen@ey.com], [eugenia.volen@ey.com]
should probalby default to Eugenia Volen if it falls into A.B@company.com
- guessing "additonal" intitials correctly, ie Bob Smith vs Bob A. Smith
This one is tricky, since you may have people with completely correctly different middle initials, ie Bob A. Smith, Bob B. Smith, and so on
- using a phone number as a unique "key", similarly to Full Name

You can futher improve by sucking in a history of emails and calculating # of emails that were sent/received by particular addresses and possibly elevate potential conflicts to "human review" (by end-user themelves maybe?) if there are a lot of emails sent/received for a particular tricky name. Otherwise, for num=1 just auto-merge to make things easier
