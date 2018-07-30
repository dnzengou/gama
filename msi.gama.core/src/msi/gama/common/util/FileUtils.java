/*********************************************************************************************
 *
 * 'FileUtils.java, in plugin msi.gama.core, is part of the source code of the GAMA modeling and simulation platform.
 * (c) 2007-2016 UMI 209 UMMISCO IRD/UPMC & Partners
 *
 * Visit https://github.com/gama-platform/gama for license information and developers contact.
 * 
 *
 **********************************************************************************************/
package msi.gama.common.util;

import static java.util.stream.Collectors.toList;

import java.io.File;
import java.net.URL;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.IFileSystem;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.emf.common.util.URI;

import msi.gama.kernel.experiment.IExperimentAgent;
import msi.gama.runtime.IScope;

/**
 * The class FileUtils.
 *
 * @author drogoul
 * @since 20 dec. 2011
 *
 */
public class FileUtils {

	static IWorkspaceRoot ROOT = ResourcesPlugin.getWorkspace().getRoot();
	static IFileSystem LOCAL = EFS.getLocalFileSystem();
	static String USER_HOME = System.getProperty("user.home");
	static final URI WORKSPACE =
			URI.createURI(ResourcesPlugin.getWorkspace().getRoot().getLocationURI().toString(), false);
	static final File CACHE;

	static {
		CACHE = new File(ROOT.getLocation().toFile().getAbsolutePath() + "/.cache");
		if (!CACHE.exists()) {
			CACHE.mkdirs();
		}
	}

	/**
	 * Checks if is absolute path.
	 *
	 * @param filePath
	 *            the file path
	 *
	 * @return true, if is absolute path
	 */
	static boolean isAbsolutePath(final String filePath) {
		// Fixes #2456
		return Paths.get(filePath).isAbsolute();
	}

	// Add a thin layer of workspace-based searching in order to resolve linked resources.
	// Should be able to catch most of the calls to relative resources as well
	static public String constructAbsoluteFilePath(final IScope scope, final String filePath, final boolean mustExist) {
		String fp;
		if (filePath.startsWith("~")) {
			fp = filePath.replaceFirst("~", USER_HOME);
		} else {
			fp = filePath;
		}
		if (isAbsolutePath(fp)) {
			final String file = findOutsideWorkspace(fp, mustExist);
			if (file != null) {
				// System.out.println("Hit with EFS-based search: " + file);
				return file;
			}
		}
		final IExperimentAgent a = scope.getExperiment();
		// Necessary to ask the workspace for the containers as projects might be linked
		final List<IContainer> paths =
				a.getWorkingPaths().stream().map(s -> ROOT.findContainersForLocation(new Path(s))[0]).collect(toList());
		for (final IContainer folder : paths) {
			final String file = findInWorkspace(fp, folder, mustExist);
			if (file != null) {
				// System.out.println("Hit with workspace-based search: " + file);
				return file;
			}
		}

		System.out.println("Falling back to the old JavaIO based search");
		return OldFileUtils.constructAbsoluteFilePathAlternate(scope, fp, mustExist);
	}

	private static String findInWorkspace(final String fp, final IContainer container, final boolean mustExist) {
		final IPath full = container.getFullPath().append(fp);
		IResource file = ROOT.getFile(full);
		if (!file.exists()) {
			// Might be a folder we're looking for
			file = ROOT.getFolder(full);
		}
		if (!file.exists()) {
			if (mustExist) { return null; }
		}
		return file.getLocation().toString();
		// getLocation() works for regular and linked files
	}

	private static String findOutsideWorkspace(final String fp, final boolean mustExist) {
		final IFileStore file = LOCAL.getStore(new Path(fp));
		if (!mustExist) { return fp; }
		final IFileInfo info = file.fetchInfo();
		if (info.exists()) { return fp; }
		return null;
	}

	/**
	 * Returns a best guess URI based on the target string and an optional URI specifying from where the relative URI
	 * should be run. If existingResource is null, then the root of the workspace is used as the relative URI
	 * 
	 * @param target
	 *            a String giving the path
	 * @param existingResource
	 *            the URI of the resource from which relative URIs should be interpreted
	 * @author Alexis Drogoul, July 2018
	 * @return an URI or null if it cannot be determined.
	 */
	public static URI getURI(final String target, final URI existingResource) {
		if (target == null) { return null; }
		try {
			final IPath path = Path.fromOSString(target);
			final IFileStore file = EFS.getLocalFileSystem().getStore(path);
			final IFileInfo info = file.fetchInfo();
			if (info.exists()) {
				// We have an absolute file
				final URI fileURI = URI.createFileURI(target);
				return fileURI;
			} else {
				final URI first = URI.createURI(target, false);
				URI root;
				if (!existingResource.isPlatformResource()) {
					root = URI.createPlatformResourceURI(existingResource.toString(), false);
				} else {
					root = existingResource;
				}
				if (root == null) {
					root = WORKSPACE;
				}
				final URI iu = first.resolve(root);
				if (isFileExistingInWorkspace(iu)) { return iu; }
				return null;
			}
		} catch (final Exception e) {
			return null;
		}
	}

	public static boolean isFileExistingInWorkspace(final URI uri) {
		if (uri == null) { return false; }
		final IFile file = getWorkspaceFile(uri);
		if (file != null) { return file.exists(); }
		return false;
	}

	public static IFile getFile(final String path, final URI root) {
		final URI uri = getURI(path, root);
		if (uri != null) {
			if (uri.isPlatformResource()) { return getWorkspaceFile(uri); }
			return linkAndGetExternalFile(path, root);
		}
		return null;
	}

	public static IFile linkAndGetExternalFile(final String path, final URI workspaceResource) {
		final IFolder folder = createExternalFolder(workspaceResource);
		if (folder == null) { return null; }
		IFile file = findExistingLinkedFile(folder, path);
		if (file != null) { return file; }
		file = correctlyNamedFile(folder, new Path(path).lastSegment());
		return createLinkedFile(path, file);
	}

	public static IFile getFileSystemFile(final URI uri, final URI workspaceResource) {
		final IFolder folder = createExternalFolder(workspaceResource);
		if (folder == null) { return null; }

		final String uriString = URI.decode(uri.isFile() ? uri.toFileString() : uri.toString());
		// We try to find an existing file linking to this uri (in case it has been
		// renamed, for instance)
		IFile file = findExistingLinkedFile(folder, uriString);
		if (file != null) { return file; }
		// We get the file with the same last name
		// If it already exists, we need to find it a new name as it doesnt point to the
		// same absolute file
		file = correctlyNamedFile(folder, uri.lastSegment());
		return createLinkedFile(uriString, file);
	}

	private static IFile createLinkedFile(final String path, final IFile file) {
		final java.net.URI javaURI = new java.io.File(path).toURI();
		try {
			file.createLink(javaURI, IResource.NONE, null);
		} catch (final CoreException e) {
			return null;
		}
		return file;
	}

	private static IFile correctlyNamedFile(final IFolder folder, final String fileName) {
		IFile file;
		String fn = fileName;
		do {
			file = folder.getFile(fn);
			fn = "copy of " + fn;
		} while (file.exists());
		return file;
	}

	private static IFile findExistingLinkedFile(final IFolder folder, final String name) {
		final IFile[] result = new IFile[1];
		try {
			folder.accept((IResourceVisitor) resource -> {
				if (resource.isLinked()) {
					final String p = resource.getLocation().toString();
					if (p.equals(name)) {
						result[0] = (IFile) resource;
						return false;
					}
				}
				return true;

			}, IResource.DEPTH_INFINITE, IResource.FILE);
		} catch (final CoreException e1) {
			e1.printStackTrace();
		}
		final IFile file = result[0];
		return file;
	}

	private static IFolder createExternalFolder(final URI workspaceResource) {
		if (workspaceResource == null || !isFileExistingInWorkspace(workspaceResource)) { return null; }
		final IFile root = getWorkspaceFile(workspaceResource);
		final IProject project = root.getProject();
		if (!project.exists()) { return null; }
		final IFolder folder = project.getFolder(new Path("external"));
		if (!folder.exists()) {
			try {
				folder.create(true, true, null);
			} catch (final CoreException e) {
				e.printStackTrace();
				return null;
			}
		}
		return folder;
	}

	public static IFile getWorkspaceFile(final URI uri) {
		final IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		final IPath uriAsPath = new Path(URI.decode(uri.toString()));
		IFile file;
		try {
			file = root.getFile(uriAsPath);
		} catch (final Exception e1) {
			return null;
		}
		if (file != null && file.exists()) { return file; }
		final String uriAsText = uri.toPlatformString(true);
		final IPath path = uriAsText != null ? new Path(uriAsText) : null;
		if (path == null) { return null; }
		try {
			file = root.getFile(path);
		} catch (final Exception e) {
			return null;
		}
		if (file != null && file.exists()) { return file; }
		return null;
	}

	public static String constructAbsoluteTempFilePath(final IScope scope, final URL url) {
		final String suffix = url.getPath().replaceAll("/", "_");
		if (!CACHE.exists()) {
			CACHE.mkdirs();
		}
		return CACHE.getAbsolutePath() + "/" + suffix;
	}

	public static void cleanCache() {
		for (final File f : CACHE.listFiles()) {
			if (!f.isDirectory()) {
				try {
					f.delete();
				} catch (final Throwable e) {
					e.printStackTrace();
				}
			}
		}
	}

	public static boolean isDirectoryOrNullExternalFile(final String path) {
		final IFileStore external = LOCAL.getStore(new Path(path));
		final IFileInfo info = external.fetchInfo();
		if (info.isDirectory() || !info.exists()) { return true; }
		return false;
	}

}
