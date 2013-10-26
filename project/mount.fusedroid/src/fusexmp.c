/*
  FUSE: Filesystem in Userspace
  Copyright (C) 2001-2007  Miklos Szeredi <miklos@szeredi.hu>
  Copyright (C) 2011       Sebastian Pipping <sebastian@pipping.org>

  This program can be distributed under the terms of the GNU GPL.
  See the file COPYING.

  gcc -Wall fusexmp.c `pkg-config fuse --cflags --libs` -o fusexmp
*/

#define FUSE_USE_VERSION 26

#ifdef HAVE_CONFIG_H
#include <config.h>
#endif

#ifdef linux
/* For pread()/pwrite()/utimensat() */
#define _XOPEN_SOURCE 700
#endif

#include <fuse.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <dirent.h>
#include <errno.h>
#include <sys/time.h>
#ifdef HAVE_SETXATTR
#include <sys/xattr.h>
#endif


struct main_opts {
    int readable;
    int writable;
    char *srcdir;
};

#define MAIN_OPT(t, p) { t, offsetof(struct main_opts, p), 1 }
static const struct fuse_opt fuse_main_opts[] = {
		MAIN_OPT("--readable", readable),
		MAIN_OPT("--writable", writable),
		FUSE_OPT_END
};

static int fuse_main_opts_proc(
                    void *data,
                    const char *arg,
                    int key,
                    struct fuse_args *outargs) {
    struct main_opts *opts = data;

    switch (key) {
    case FUSE_OPT_KEY_NONOPT:
        if (!opts->srcdir) {
            char srcdir[PATH_MAX];
            if (realpath(arg, srcdir) == 0) {
                fprintf(stderr, "Error: `%s' %s\n", arg, strerror(errno));
                return -1;
            }
            return fuse_opt_add_opt(&opts->srcdir, srcdir);
        }
        break;
    default:
        break;
    }
    return 1;
}

static int target(char *out, const char *path) {
	struct main_opts *opts = fuse_get_context()->private_data;
	int len = strlen(opts->srcdir);
	if (memcpy(out, opts->srcdir, len) < 0) {
		return -1;
	}
	if (memcpy(out + len, path, strlen(path) + 1) < 0) {
		return -1;
	}

	return 0;
}


static int xmp_getattr(const char *path, struct stat *stbuf)
{
	int res;
	char target_path[PATH_MAX];
	res = target(target_path, path);
	if (res != 0) {
		return -errno;
	}

	res = lstat(target_path, stbuf);
	if (res == -1) {
		return -errno;
	}

	struct main_opts *opts = fuse_get_context()->private_data;
	if (opts->readable) {
		stbuf->st_mode |= S_IRUSR | S_IRGRP | S_IROTH;
	}
	if (opts->writable) {
		stbuf->st_mode |= S_IWUSR | S_IWGRP | S_IWOTH;
	}

	return 0;
}

static int xmp_access(const char *path, int mask)
{
	struct main_opts *opts = fuse_get_context()->private_data;
	if (opts->readable || opts->writable) {
		return 0;
	} else {
			int res;
			char target_path[PATH_MAX];
			res = target(target_path, path);
			if (res != 0) {
				return -errno;
			}

			res = access(target_path, mask);
			if (res == -1) {
				return -errno;
			}
			return 0;
	}
}

static int xmp_readlink(const char *path, char *buf, size_t size)
{
	int res;
	char target_path[PATH_MAX];
	res = target(target_path, path);
	if (res != 0) {
		return -errno;
	}

	res = readlink(target_path, buf, size - 1);
	if (res == -1)
		return -errno;

	buf[res] = '\0';
	return 0;
}


static int xmp_readdir(const char *path, void *buf, fuse_fill_dir_t filler,
		       off_t offset, struct fuse_file_info *fi)
{
	int res;
	char target_path[PATH_MAX];
	res = target(target_path, path);
	if (res != 0) {
		return -errno;
	}

	DIR *dp;
	struct dirent *de;

	(void) offset;
	(void) fi;

	dp = opendir(target_path);
	if (dp == NULL)
		return -errno;

	while ((de = readdir(dp)) != NULL) {
		struct stat st;
		memset(&st, 0, sizeof(st));
		st.st_ino = de->d_ino;
		st.st_mode = de->d_type << 12;
		if (filler(buf, de->d_name, &st, 0))
			break;
	}

	closedir(dp);
	return 0;
}

static int xmp_mknod(const char *path, mode_t mode, dev_t rdev)
{
	int res;
	char target_path[PATH_MAX];
	res = target(target_path, path);
	if (res != 0) {
		return -errno;
	}

	/* On Linux this could just be 'mknod(path, mode, rdev)' but this
	   is more portable */
	if (S_ISREG(mode)) {
		res = open(target_path, O_CREAT | O_EXCL | O_WRONLY, mode);
		if (res >= 0)
			res = close(res);
	} else if (S_ISFIFO(mode))
		res = mkfifo(target_path, mode);
	else
		res = mknod(target_path, mode, rdev);
	if (res == -1)
		return -errno;

	return 0;
}

static int xmp_mkdir(const char *path, mode_t mode)
{
	int res;
	char target_path[PATH_MAX];
	res = target(target_path, path);
	if (res != 0) {
		return -errno;
	}

	res = mkdir(target_path, mode);
	if (res == -1)
		return -errno;

	return 0;
}

static int xmp_unlink(const char *path)
{
	int res;
	char target_path[PATH_MAX];
	res = target(target_path, path);
	if (res != 0) {
		return -errno;
	}

	res = unlink(target_path);
	if (res == -1)
		return -errno;

	return 0;
}

static int xmp_rmdir(const char *path)
{
	int res;
	char target_path[PATH_MAX];
	res = target(target_path, path);
	if (res != 0) {
		return -errno;
	}

	res = rmdir(target_path);
	if (res == -1)
		return -errno;

	return 0;
}

static int xmp_symlink(const char *from, const char *to)
{
	int res;
	char target_from[PATH_MAX];
	res = target(target_from, from);
	if (res != 0) {
		return -errno;
	}
	char target_to[PATH_MAX];
	res = target(target_to, to);
	if (res != 0) {
		return -errno;
	}

	res = symlink(target_from, target_to);
	if (res == -1)
		return -errno;

	return 0;
}

static int xmp_rename(const char *from, const char *to)
{
	int res;
	char target_from[PATH_MAX];
	res = target(target_from, from);
	if (res != 0) {
		return -errno;
	}
	char target_to[PATH_MAX];
	res = target(target_to, to);
	if (res != 0) {
		return -errno;
	}

	res = rename(target_from, target_to);
	if (res == -1)
		return -errno;

	return 0;
}

static int xmp_link(const char *from, const char *to)
{
	int res;
	char target_from[PATH_MAX];
	res = target(target_from, from);
	if (res != 0) {
		return -errno;
	}
	char target_to[PATH_MAX];
	res = target(target_to, to);
	if (res != 0) {
		return -errno;
	}

	res = link(target_from, target_to);
	if (res == -1)
		return -errno;

	return 0;
}

static int xmp_chmod(const char *path, mode_t mode)
{
	int res;
	char target_path[PATH_MAX];
	res = target(target_path, path);
	if (res != 0) {
		return -errno;
	}

	res = chmod(target_path, mode);
	if (res == -1)
		return -errno;

	return 0;
}

static int xmp_chown(const char *path, uid_t uid, gid_t gid)
{
	int res;
	char target_path[PATH_MAX];
	res = target(target_path, path);
	if (res != 0) {
		return -errno;
	}

	res = lchown(target_path, uid, gid);
	if (res == -1)
		return -errno;

	return 0;
}

static int xmp_truncate(const char *path, off_t size)
{
	int res;
	char target_path[PATH_MAX];
	res = target(target_path, path);
	if (res != 0) {
		return -errno;
	}

	res = truncate(target_path, size);
	if (res == -1)
		return -errno;

	return 0;
}

#ifdef HAVE_UTIMENSAT
static int xmp_utimens(const char *path, const struct timespec ts[2])
{
	int res;
	char target_path[PATH_MAX];
	res = target(target_path, path);
	if (res != 0) {
		return -errno;
	}

	/* don't use utime/utimes since they follow symlinks */
	res = utimensat(0, target_path, ts, AT_SYMLINK_NOFOLLOW);
	if (res == -1)
		return -errno;

	return 0;
}
#endif

static int xmp_open(const char *path, struct fuse_file_info *fi)
{
	int res;
	char target_path[PATH_MAX];
	res = target(target_path, path);
	if (res != 0) {
		return -errno;
	}

	res = open(target_path, fi->flags);
	if (res == -1)
		return -errno;

	close(res);
	return 0;
}

static int xmp_read(const char *path, char *buf, size_t size, off_t offset,
		    struct fuse_file_info *fi)
{
	int fd;
	int res;
	char target_path[PATH_MAX];
	res = target(target_path, path);
	if (res != 0) {
		return -errno;
	}

	(void) fi;
	fd = open(target_path, O_RDONLY);
	if (fd == -1)
		return -errno;

	res = pread(fd, buf, size, offset);
	if (res == -1)
		res = -errno;

	close(fd);
	return res;
}

static int xmp_write(const char *path, const char *buf, size_t size,
		     off_t offset, struct fuse_file_info *fi)
{
	int fd;
	int res;
	char target_path[PATH_MAX];
	res = target(target_path, path);
	if (res != 0) {
		return -errno;
	}

	(void) fi;
	fd = open(target_path, O_WRONLY);
	if (fd == -1)
		return -errno;

	res = pwrite(fd, buf, size, offset);
	if (res == -1)
		res = -errno;

	close(fd);
	return res;
}

#ifndef __ANDROID__
static int xmp_statfs(const char *path, struct statvfs *stbuf)
{
	int res;
	char target_path[PATH_MAX];
	res = target(target_path, path);
	if (res != 0) {
		return -errno;
	}

	res = statvfs(target_path, stbuf);
	if (res == -1)
		return -errno;

	return 0;
}
#endif

static int xmp_release(const char *path, struct fuse_file_info *fi)
{
	/* Just a stub.	 This method is optional and can safely be left
	   unimplemented */

	(void) path;
	(void) fi;
	return 0;
}

static int xmp_fsync(const char *path, int isdatasync,
		     struct fuse_file_info *fi)
{
	/* Just a stub.	 This method is optional and can safely be left
	   unimplemented */

	(void) path;
	(void) isdatasync;
	(void) fi;
	return 0;
}

#ifdef HAVE_POSIX_FALLOCATE
static int xmp_fallocate(const char *path, int mode,
			off_t offset, off_t length, struct fuse_file_info *fi)
{
	int fd;
	int res;
	char target_path[PATH_MAX];
	res = target(target_path, path);
	if (res != 0) {
		return -errno;
	}

	(void) fi;

	if (mode)
		return -EOPNOTSUPP;

	fd = open(target_path, O_WRONLY);
	if (fd == -1)
		return -errno;

	res = -posix_fallocate(fd, offset, length);

	close(fd);
	return res;
}
#endif

#ifdef HAVE_SETXATTR
/* xattr operations are optional and can safely be left unimplemented */
static int xmp_setxattr(const char *path, const char *name, const char *value,
			size_t size, int flags)
{
	int res;
	char target_path[PATH_MAX];
	res = target(target_path, path);
	if (res != 0) {
		return -errno;
	}

	res = lsetxattr(target_path, name, value, size, flags);
	if (res == -1)
		return -errno;
	return 0;
}

static int xmp_getxattr(const char *path, const char *name, char *value,
			size_t size)
{
	int res;
	char target_path[PATH_MAX];
	res = target(target_path, path);
	if (res != 0) {
		return -errno;
	}

	res = lgetxattr(target_path, name, value, size);
	if (res == -1)
		return -errno;
	return res;
}

static int xmp_listxattr(const char *path, char *list, size_t size)
{
	int res;
	char target_path[PATH_MAX];
	res = target(target_path, path);
	if (res != 0) {
		return -errno;
	}

	res = llistxattr(target_path, list, size);
	if (res == -1)
		return -errno;
	return res;
}

static int xmp_removexattr(const char *path, const char *name)
{
	int res;
	char target_path[PATH_MAX];
	res = target(target_path, path);
	if (res != 0) {
		return -errno;
	}

	res = lremovexattr(target_path, name);
	if (res == -1)
		return -errno;
	return 0;
}
#endif /* HAVE_SETXATTR */

static struct fuse_operations xmp_oper = {
	.getattr	= xmp_getattr,
	.access		= xmp_access,
	.readlink	= xmp_readlink,
	.readdir	= xmp_readdir,
	.mknod		= xmp_mknod,
	.mkdir		= xmp_mkdir,
	.symlink	= xmp_symlink,
	.unlink		= xmp_unlink,
	.rmdir		= xmp_rmdir,
	.rename		= xmp_rename,
	.link		= xmp_link,
	.chmod		= xmp_chmod,
	.chown		= xmp_chown,
	.truncate	= xmp_truncate,
#ifdef HAVE_UTIMENSAT
	.utimens	= xmp_utimens,
#endif
	.open		= xmp_open,
	.read		= xmp_read,
	.write		= xmp_write,
#ifndef __ANDROID__
	.statfs		= xmp_statfs,
#endif
	.release	= xmp_release,
	.fsync		= xmp_fsync,
#ifdef HAVE_POSIX_FALLOCATE
	.fallocate	= xmp_fallocate,
#endif
#ifdef HAVE_SETXATTR
	.setxattr	= xmp_setxattr,
	.getxattr	= xmp_getxattr,
	.listxattr	= xmp_listxattr,
	.removexattr	= xmp_removexattr,
#endif
};

int main(int argc, char *argv[])
{
	struct fuse_args args = FUSE_ARGS_INIT(argc, argv);

	struct main_opts opts;
	memset(&opts, 0, sizeof(opts));

	int res;
	res = fuse_opt_parse(
			&args,
			&opts,
			fuse_main_opts,
			fuse_main_opts_proc);

	return fuse_main(args.argc, args.argv, &xmp_oper, &opts);
}
