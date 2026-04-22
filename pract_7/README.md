# COS332 - Practical Assignment 7
## Mailbox Manager

---

## What This Program Does

This program connects to a mail server and lets you manage your emails
from the command line. It uses the POP3 protocol to communicate with
the server — meaning it talks to the server using raw socket commands,
with no email libraries.

---

## Features

1. Show all messages (sender, subject, size, status)
2. Read a full message
3. Search messages by keyword
4. Delete one or multiple messages
5. Shows total message count and mailbox size on login

---

## Requirements

- Java (JDK 8 or higher)
- A running POP3 mail server (e.g. Dovecot)
- A running SMTP mail server (e.g. Postfix)
- Linux / WSL (Ubuntu recommended)

---

## How To Set Up

### 1. Install Java
```bash
sudo apt update
sudo apt install default-jdk -y
java -version
```

### 2. Install Dovecot (POP3 server)
```bash
sudo apt install dovecot-pop3d -y
sudo service dovecot start
```

### 3. Install mailutils (to send test emails)
```bash
sudo apt install mailutils -y
```

### 4. Create a test user
```bash
sudo adduser testuser
```

### 5. Send test emails
```bash
echo "Hello there" | sudo mail -s "Test email 1" testuser
echo "Meeting at 3pm" | sudo mail -s "Meeting" testuser
echo "Delete this" | sudo mail -s "Spam" testuser
```

---

## How To Compile and Run

```bash
# Navigate to the project folder
cd ~/prac7

# Compile
javac MailboxManager.java

# Run
java MailboxManager
```

---

## Configuration

Open `MailboxManager.java` and change these four lines at the top:

```java
static String host     = "127.0.0.1";   // your server IP
static int    port     = 110;            // POP3 port
static String username = "testuser";     // your mail username
static String password = "test123";      // your mail password
```

---

## Menu Options

```
1. Show all messages   - displays a table of all emails
2. Read a message      - downloads and prints a full email
3. Search messages     - filter emails by keyword
4. Delete message(s)   - mark one or more emails for deletion
5. Quit                - disconnects (deletions happen here)
```

---

## Important Note About Deletion

Messages are only permanently deleted when you choose **Quit (option 5)**.
Until then, deletions can be seen in the table as **[DELETED]** but the
emails are still on the server. This is how POP3 works — the QUIT command
commits all deletions.

---

## Files

| File | Description |
|------|-------------|
| MailboxManager.java | Main program source code |
| README.md | This file |
| DOCUMENTATION.docx | Full technical documentation |

---

## Author

COS332 - Computer Networks
University of Pretoria
2026
