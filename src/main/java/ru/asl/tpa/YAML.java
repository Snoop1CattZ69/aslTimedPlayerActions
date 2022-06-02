package ru.asl.tpa;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import org.bukkit.plugin.java.JavaPlugin;

public class YAML {

	protected YamlConfiguration	yaml	= new YamlConfiguration();
	protected File				file;

	public YAML(File file, JavaPlugin plugin, String extendedPath) {
		this.file = file;
		try {
			if (fileExists())
				load();
			else {

				if (!file.getParentFile().exists())
					file.getParentFile().mkdirs();

				if (plugin != null && plugin.getResource(file.getName()) != null) {
					final String ex = extendedPath;
					if (ex == null)
						exportFile(file.getName(), plugin, plugin.getDataFolder());
					else
						exportFile(file.getName(), plugin, plugin.getDataFolder() + "/" + ex);
					load();
				}
				else {
					file.createNewFile();
					load();
				}
			}
		} catch (IOException | InvalidConfigurationException e) {
			file = null;
			yaml = null;
			e.printStackTrace();
		}
	}

	public YAML(String path, JavaPlugin plugin) { this(new File(path), plugin, null); }

	public YAML(String path) { this(new File(path), null, null); }

	public YAML(File file) { this(file, null, null); }

	public boolean contains(String path) {
		return yaml.contains(path);
	}

	protected boolean fileExists() { return file.exists(); }

	public double getDouble(String path) {
		return yaml.getDouble(path);
	}

	public double getDouble(String path, double def, boolean restore) {
		if (restore) if (this.getString(path) == null) set(path, def);
		return this.getDouble(path);
	}

	public File getFile() { return file; }

	public int getInt(String path) {
		final long request = this.getLong(path);
		return request <= Integer.MIN_VALUE ? Integer.MIN_VALUE : request >= Integer.MAX_VALUE ? Integer.MAX_VALUE : Long.valueOf(request).intValue();
	}

	public int getInt(String path, int def, boolean restore) {
		if (restore) if (this.getString(path) == null) set(path, def);
		return this.getInt(path);
	}

	public List<Integer> getIntList(String path) { return yaml.getIntegerList(path); }

	public Set<String> getKeys(boolean deep) {
		return yaml.getKeys(deep);
	}

	public long getLong(String path) {
		return yaml.getLong(path);
	}

	public long getLong(String path, long def, boolean restore) {
		if (restore) if (this.getString(path) == null) set(path, def);
		return this.getLong(path);
	}

	public ConfigurationSection getSection(String section) {
		return yaml.getConfigurationSection(section);
	}

	public String getString(String path) {
		return yaml.getString(path);
	}

	public String getString(String path, String def, boolean restore) {
		if (restore) if (this.getString(path) == null) set(path, def);
		return this.getString(path);
	}

	public List<String>  getStringList(String path) {
		return yaml.getStringList(path);
	}

	public void load() throws FileNotFoundException, IOException, InvalidConfigurationException { yaml.load(file); }

	public void reload() {
		try {
			load();
		} catch (IOException | InvalidConfigurationException e) {
			e.printStackTrace();
		}
	}

	public void save() { try { yaml.save(file); } catch (final IOException e) { e.printStackTrace(); } }

	public void set(String path, Object value) {
		yaml.set(path, value);
		save();
	}

	public static String getFileExtension(File file) {
		final String fileName = file.getName();

		if (fileName.lastIndexOf(".") > 0) return fileName.substring(fileName.lastIndexOf(".") + 1);
		else return "";
	}

	public static boolean hasFileInJar(String jarPath, JavaPlugin from) {
		return from.getClass().getResourceAsStream("/" + jarPath) != null;
	}

	private static void exportFile(String jarPath, JavaPlugin from, String toFolder) {
		exportFile(jarPath, from, new File(toFolder));
	}

	public static void exportFile(String jarPath, JavaPlugin from, File toFolder) {
		if (from.getClass().getResourceAsStream("/" + jarPath) != null) {
			final String[] split = jarPath.split("/");

			final File toFile = new File(toFolder.getPath(), split[split.length-1]);
			if (toFile.exists()) return;

			try (
					FileOutputStream out = new FileOutputStream(toFile);
					InputStream in = from.getClass().getResourceAsStream("/" + jarPath)
					) {
				if (!toFile.exists())
					toFile.createNewFile();

				final byte[] buffer = new byte[1024];

				int read;
				while ((read = in.read(buffer)) != -1) {
					out.write(buffer, 0, read);
				}

			} catch (final IOException e) {
				e.printStackTrace();
				return;
			}

		}
	}

}
