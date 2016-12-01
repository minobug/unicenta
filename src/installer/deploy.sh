echo 'password: '$1
echo 'target directory: '$2
echo 'source file: '$3

sshpass -p $1 sftp unicenta-hugh@frs.sourceforge.net:$2 <<END
put $3
END
echo -e  '\n\rupload complete'