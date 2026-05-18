# COS332_Prac9
Prac 9 SUE ON THE 18TH 
# COS332 Prac 9 - SMTP Proxy

## What this does
This is an SMTP proxy that sits between an email client and a real email server.
It intercepts emails and does the following:
- Replaces bad Newspeak words with good ones
- Adds a disclaimer at the bottom of every email
- Blocks emails containing the word "Illuminati"

## What you need to download and install
1. Java (JDK) - to run the proxy
2. WSL (Windows Subsystem for Linux) - already on Windows 11
3. Postfix (SMTP server) - installed on WSL
4. Dovecot (POP3 server) - installed on WSL
5. Thunderbird (email client)

## Setup Steps

### 1. Install WSL
- Press Windows key, type "wsl", press Enter

### 2. Install Postfix and Dovecot in WSL
```bash
sudo apt update
sudo apt install postfix -y
sudo apt install dovecot-pop3d -y
```

### 3. Start the servers
```bash
sudo postfix start
sudo service dovecot start
```

### 4. Find your WSL IP address
```bash
hostname -I
```
Copy this IP address - you will need it!

### 5. Configure Postfix to accept outside connections
```bash
sudo postconf -e "inet_interfaces = all"
sudo postfix stop
sudo postfix start
```

### 6. Download the files
- Download BOTH SMTPProxy.java AND SMTPProxy.class
- Put them in the same folder

### 7. Update the WSL IP in the code
- Open SMTPProxy.java
- Find this line: static final String SMTP_HOST = "172.24.110.220";
- Replace the IP with YOUR WSL IP from step 4

### 8. Compile and run the proxy


### 9. Set up Thunderbird
- Incoming server: localhost, port 110, POP3, No SSL
- Outgoing server: localhost, port 55555, No SSL
- Username: root
- Password: your Linux root password

### 10. Set Linux root password
```bash
passwd root
```

## IMPORTANT - MD5 Hash
- Do NOT recompile the code or the MD5 hash will change!
- Use the SMTPProxy.class file from this repository directly
- MD5 hash of SMTPProxy.class: 0387fad2b831ff0c57fa117a9804868f

## Testing
Send an email in Thunderbird with these words:
- bad, warm, fast, slow, ran, stole, better, best
- Check the received email - words should be replaced!
