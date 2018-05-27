Jabber Roster Migration
=======================

This tool simplifies migrating of a roster (contact list in XMPP terminology) from one Jabber server (including GTalk!) to another.


How to get it
-------------

You can download an archive with compiled code, all JAR dependencies and `roster-migrate` script from the [releases page](https://github.com/catap/jabber-migrate/releases/), or build it yourself using Maven.

The only runtime requirement is Java 1.8+.
If you want to use `roster-migrate` shell script, then you’ll also need some POSIX-compatible shell.


Usage
-----

    Usage: roster-migrate [options] [command] [command options]
      Options:
        --help, -H
          Display help
        --host, --H
          Host to overwrite value from SRV records
      * --jid, -j
          JID to perform an action
      * --password, -w
          password to connect as provided JID
        --port, -p
          Port that will be used when host is overwritten from SRV records
          Default: 5222
      Commands:
        import      Import existed file into roster
          Usage: import [options]
            Options:
              --adium
                Roster file in Adium (blist.xml) format
                Default: false
              -f, --file
                Roster file path by default is stdout/stdin
    
        export      Export roster into specified file
          Usage: export [options]
            Options:
              -f, --file
                Roster file path by default is stdout/stdin
              --onlyUnreachable
                Creates a list of users that can't be reach anymore
                Default: false

Roster export:

    $ ./bin/roster-migrate --jid test@jabber.org -w export -f export.txt

Roster import:

    $ ./bin/roster-migrate --jid test@jabber.org -w import -f export.txt

If you would like yo you stdin as source of roster you should define a password as `-w` optional argument
 or put it as first line inside `export.txt` and yous `... import < export.txt`


Import/export format
--------------------

The exported format contains one line for every contact.

Format:

    <isRemove>;<nickname>;<user>;[<groups>]

* isRemove ... `-` to remove contact, `+` to add contact
* nickname ... contact nickname
* user     ... contact ID
* groups   ... comma-separated list of groups

Examples:

    +;Sam;somebody@jabber.cz;[Friends]
    -;alien;123@icq;[]
    +;alien;123@icq.netlab.cz;[Sales,Travel]

Import from Adium
-----------------

If you would like to import your old contacts from adium to new jabber server, you can use this tools by

    $ ./bin/roster-migrate --jid test@jabber.org -w import --adium -f ~/Library/Application\ Support/Adium\ 2.0/Users/Default/Contact\ List.plist

Cleanup roster
--------------

For last time a lot of services shutdown their s2s (for example gmail, ya.ru and many on them).
This tools also provide an easy way to create a list of users that may be removed because they are unreachable.

    $ ./bin/roster-migrate --jid test@jabber.org -w export --onlyUnreachable -f export.txt

Unreachable domain means:
 - hasn't got any reachable address (over SRV records or direct connect to 5269)
 - your servers returned error that remote-server-not-found

Unreachable JID means:
 - your server's returned error that recipient-unavailable
 - domain is reachable and script can register JID on it

Origin
------

This project is a fork of http://sourceforge.net/projects/migrate/ by [Ivo Danihelka](https://github.com/fidlej).
