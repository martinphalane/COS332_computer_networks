----start ldap 
sudo systemctl start slapd

---
# Check what's actually in your tree right now
ldapsearch -x -D "cn=admin,dc=martinairways,dc=com" -w admin -b "dc=martinairways,dc=com" "(objectClass=*)" dn

-- only the root entry exists. ou=Planes was never created. Load the LDIF now:
ldapadd -x -D "cn=admin,dc=martinairways,dc=com" -w admin -f planes.ldif

---Verify if files LDIF was loaded 
ldapsearch -x -D "cn=admin,dc=martinairways,dc=com" -w admin -b "dc=martinairways,dc=com" "(objectClass=*)" dn