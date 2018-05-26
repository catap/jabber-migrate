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

    $ ./bin/roster-migrate MODE <options>

     MODE                 : export, or import
     --help               : Show help
     -a (--adium)         : Roster file path in Adium (blist.xml) format, import
                            only (default is off)
     -f (--file) PATH     : Roster file path (default is stdout/stdin)
     -h (--host) HOST     : Server hostname (default is same as service name)
     -p (--port) PORT     : Server port (default is 5222)
     -s (--service) HOST  : Service (domain) name; the portion of JID after at (@)
                            sign.
     -u (--username) NAME : Username; usually the portion of JID before at (@)
                            sign, but some severs uses whole JID as an username.
     -w (--password) PASS : Password

Roster export:

    $ ./bin/roster-migrate export -u kevin@flynn.com -s flynn.com -w top-secret -h talk.google.com -f export.txt

Roster import:

    $ ./bin/roster-migrate import -u flynn -s jabbim.cz -w top-secret < export.txt



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

    $ ./bin/roster-migrate import -u flynn -s jabbim.cz -w top-secret -a -f ~/Library/Application\ Support/Adium\ 2.0/Users/Default/Contact\ List.plist

Origin
------

This project is a fork of http://sourceforge.net/projects/migrate/ by [Ivo Danihelka](https://github.com/fidlej).
