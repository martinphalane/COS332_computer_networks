# Prac 8 - Automatic File Backup Program
## COS 332 - Computer Networks

This program watches a folder on your computer.
When a file is added or changed, it automatically uploads it to a backup server using FTP.

---

## Files in this project

| File | What it does |
|---|---|
| `Main.java` | Starts the program and runs the backup loop |
| `Settings.java` | All your config — IP, folder paths, login details |
| `FolderMonitor.java` | Watches the folder for new or changed files |
| `FTPConnection.java` | Connects and logs into the FTP server |
| `FTPUploader.java` | Uploads a single file to the server |

---

## How to set up WSL + FTP server

### Step 1 — Open WSL
Open your terminal and type:
```
wsl
```

### Step 2 — Install the FTP server (vsftpd)
```
sudo apt update
sudo apt install vsftpd -y
```

### Step 3 — Configure the FTP server
Open the config file:
```
sudo nano /etc/vsftpd.conf
```
Find these lines and make sure they look exactly like this
(remove the # at the start if there is one):
```
anonymous_enable=NO
local_enable=YES
write_enable=YES
local_umask=022
chroot_local_user=NO
pasv_enable=YES
pasv_min_port=40000
pasv_max_port=50000
```
Save the file: press Ctrl+X, then Y, then Enter.

### Step 4 — Create a backup folder on the server
```
sudo mkdir -p /backup
sudo chmod 777 /backup
```

### Step 5 — Restart the FTP server
```
sudo service vsftpd restart
```

### Step 6 — Find your WSL IP address
```
hostname -I
```
Copy the IP address shown — you will need it in the next step.
It will look something like: 172.24.32.10

---

## How to configure the program

Open `Settings.java` and update these values:

```java
static String SERVER_IP     = "172.24.32.10";  // paste your WSL IP here
static int    SERVER_PORT   = 21;
static String USERNAME      = "your-wsl-username"; // your WSL login name
static String PASSWORD      = "your-wsl-password"; // your WSL login password
static String WATCH_FOLDER  = "/mnt/c/Users/YourName/Documents/watch"; // folder to watch
static String BACKUP_FOLDER = "/backup/";
static int    CHECK_EVERY   = 20;
```

### Create the watch folder
```
mkdir -p /mnt/c/Users/YourName/Documents/watch
```
(replace YourName with your actual Windows username)

---

## How to compile and run

### Step 1 — Go to the project folder
```
cd /path/to/prac8
```

### Step 2 — Compile all files
```
javac *.java
```
You should see no errors. If you do, check that Java is installed:
```
java -version
```
If not installed:
```
sudo apt install default-jdk -y
```

### Step 3 — Run the program
```
java Main
```
You should see:
```
== File Backup Program ==

Watching: /mnt/c/Users/YourName/Documents/watch
Found 0 existing file(s).
Checking every 20 seconds...
```

---

## How to test it

Open a second terminal window and drop a text file into your watch folder:
```
echo "hello world" > /mnt/c/Users/YourName/Documents/watch/test.txt
```
Wait up to 60 seconds. You should see in the program window:
```
  New file: test.txt
1 file(s) changed. Uploading...
STATUS: Connecting to 172.24.32.10...
SENT: USER your-username
RECEIVED: 331 Please specify the password.
SENT: PASS your-password
RECEIVED: 230 Login successful.
...
SUCCESS: Uploaded test.txt
STATUS: Connection closed.
```

Then verify the file is on the server:
```
ls /backup/
```
You should see `test.txt` there.

---

## How to generate your MD5 digest (required for submission)

After compiling, run this on each .class file:
```
md5sum Main.class
md5sum FTPConnection.class
md5sum FTPUploader.class
md5sum FolderMonitor.class
md5sum Settings.class
```
Save these values — you will need to submit them on clickUP.

---

## How to stop the program
Press `Ctrl+C` in the terminal where the program is running.

---

## Common problems

| Problem | Fix |
|---|---|
| `Connection refused` | Run `sudo service vsftpd restart` and check your IP in Settings.java |
| `No such file or directory` | Make sure the watch folder and /backup folder both exist |
| `Login incorrect` | Double check your WSL username and password in Settings.java |
| `javac not found` | Run `sudo apt install default-jdk -y` |
| `530 This FTP server is anonymous only` | Make sure `anonymous_enable=NO` and `local_enable=YES` in vsftpd.conf |

