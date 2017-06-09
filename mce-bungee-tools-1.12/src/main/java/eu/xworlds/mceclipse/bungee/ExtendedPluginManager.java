/*
    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package eu.xworlds.mceclipse.bungee;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Stack;
import java.util.logging.Level;

import org.yaml.snakeyaml.Yaml;

import com.google.common.base.Preconditions;

import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Event;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginClassloader;
import net.md_5.bungee.api.plugin.PluginDescription;
import net.md_5.bungee.api.plugin.PluginManager;

/**
 * @author mepeisen
 *
 */
public class ExtendedPluginManager extends PluginManager
{
    
    /** delegating */
    private PluginManager                  delegate;
    
    /** to load map. */
    private Map<String, ProjectDescriptor> projectsToLoad = new HashMap<>();
    
    /** to load map. */
    private Map<String, PluginDescription> pluginsToLoad;
    
    /** yaml. */
    private final Yaml                     yaml;
    
    /** bungeecord proxy server. */
    private ProxyServer                    bungee;
    
    /** the plugins. */
    private final Map<String, Plugin>      plugins;
    
    /**
     * Constructor
     * 
     * @param bungee
     * @throws Exception
     */
    public ExtendedPluginManager(BungeeCord bungee) throws Exception
    {
        super(bungee);
        this.bungee = bungee;
        this.delegate = bungee.pluginManager;
        
        Field field = PluginManager.class.getDeclaredField("yaml"); //$NON-NLS-1$
        field.setAccessible(true);
        this.yaml = (Yaml) field.get(this.delegate);
        
        field = PluginManager.class.getDeclaredField("toLoad"); //$NON-NLS-1$
        field.setAccessible(true);
        this.pluginsToLoad = (Map<String, PluginDescription>) field.get(this.delegate);
        
        field = PluginManager.class.getDeclaredField("plugins"); //$NON-NLS-1$
        field.setAccessible(true);
        this.plugins = (Map<String, Plugin>) field.get(this.delegate);
    }
    
    @Override
    public void registerCommand(Plugin plugin, Command command)
    {
        this.delegate.registerCommand(plugin, command);
    }
    
    @Override
    public void unregisterCommand(Command command)
    {
        this.delegate.unregisterCommand(command);
    }
    
    @Override
    public void unregisterCommands(Plugin plugin)
    {
        this.delegate.unregisterCommands(plugin);
    }
    
    @Override
    public boolean dispatchCommand(CommandSender sender, String commandLine)
    {
        return this.delegate.dispatchCommand(sender, commandLine);
    }
    
    @Override
    public boolean dispatchCommand(CommandSender sender, String commandLine, List<String> tabResults)
    {
        return this.delegate.dispatchCommand(sender, commandLine, tabResults);
    }
    
    @Override
    public Collection<Plugin> getPlugins()
    {
        return this.delegate.getPlugins();
    }
    
    @Override
    public Plugin getPlugin(String name)
    {
        return this.delegate.getPlugin(name);
    }
    
    @Override
    public void loadPlugins()
    {
        try
        {
            // rewrite projects
            for (final Map.Entry<String, PluginDescription> entry : this.pluginsToLoad.entrySet())
            {
                final ProjectDescriptor prj = new ProjectDescriptor();
                prj.description = entry.getValue();
                prj.classpath = new URL[] { entry.getValue().getFile().toURI().toURL() };
                this.projectsToLoad.put(entry.getKey(), prj);
            }
            
            // load
            final Map<PluginDescription, Boolean> pluginStatuses = new HashMap<>();
            for (Map.Entry<String, ProjectDescriptor> entry : this.projectsToLoad.entrySet())
            {
                ProjectDescriptor plugin = entry.getValue();
                if (!(enablePlugin(pluginStatuses, new Stack<PluginDescription>(), plugin)))
                {
                    ProxyServer.getInstance().getLogger().log(Level.WARNING, "Failed to enable {0}", entry.getKey());
                }
            }
            
            // clear values
            this.projectsToLoad.clear();
            this.projectsToLoad = null;
            this.pluginsToLoad.clear();
            final Field field = PluginManager.class.getDeclaredField("toLoad"); //$NON-NLS-1$
            field.setAccessible(true);
            field.set(this.delegate, null);
        }
        catch (Exception ex)
        {
            throw new IllegalStateException(ex);
        }
    }
    
    /**
     * Enable plugins
     * 
     * @param pluginStatuses
     * @param dependStack
     * @param plugin
     * @return true on success
     */
    private boolean enablePlugin(Map<PluginDescription, Boolean> pluginStatuses, Stack<PluginDescription> dependStack, ProjectDescriptor plugin)
    {
        if (pluginStatuses.containsKey(plugin))
        {
            return pluginStatuses.get(plugin).booleanValue();
        }
        
        Set<String> dependencies = new HashSet<>();
        dependencies.addAll(plugin.description.getDepends());
        dependencies.addAll(plugin.description.getSoftDepends());
        
        boolean status = true;
        
        for (String dependName : dependencies)
        {
            ProjectDescriptor depend = this.projectsToLoad.get(dependName);
            Boolean dependStatus = (depend != null) ? (Boolean) pluginStatuses.get(depend.description) : Boolean.FALSE;
            
            if (dependStatus == null)
            {
                if (dependStack.contains(depend.description))
                {
                    StringBuilder dependencyGraph = new StringBuilder();
                    for (PluginDescription element : dependStack)
                    {
                        dependencyGraph.append(element.getName()).append(" -> "); //$NON-NLS-1$
                    }
                    dependencyGraph.append(plugin.description.getName()).append(" -> ").append(dependName); //$NON-NLS-1$
                    ProxyServer.getInstance().getLogger().log(Level.WARNING, "Circular dependency detected: {0}", dependencyGraph); //$NON-NLS-1$
                    status = false;
                }
                else
                {
                    dependStack.push(plugin.description);
                    dependStatus = Boolean.valueOf(enablePlugin(pluginStatuses, dependStack, depend));
                    dependStack.pop();
                }
            }
            
            if ((dependStatus == Boolean.FALSE) && (plugin.description.getDepends().contains(dependName)))
            {
                ProxyServer.getInstance().getLogger().log(Level.WARNING, "{0} (required by {1}) is unavailable", new Object[] { String.valueOf(dependName), plugin.description.getName() }); //$NON-NLS-1$
                
                status = false;
            }
            
            if (!(status))
            {
                break;
            }
            
        }
        
        if (status)
        {
            try
            {
                Object loader = new PluginClassloader(plugin.classpath);
                
                Class<?> main = ((URLClassLoader) loader).loadClass(plugin.description.getMain());
                Plugin clazz = (Plugin) main.getDeclaredConstructor(new Class[0]).newInstance(new Object[0]);
                
                final Method mth = Plugin.class.getDeclaredMethod("init", ProxyServer.class, PluginDescription.class);
                mth.setAccessible(true);
                mth.invoke(clazz, this.bungee, plugin.description);
                this.plugins.put(plugin.description.getName(), clazz);
                clazz.onLoad();
                ProxyServer.getInstance().getLogger().log(Level.INFO, "Loaded plugin {0} version {1} by {2}",
                        new Object[] { plugin.description.getName(), plugin.description.getVersion(), plugin.description.getAuthor() });
            }
            catch (Throwable t)
            {
                this.bungee.getLogger().log(Level.WARNING, new StringBuilder().append("Error enabling plugin ").append(plugin.description.getName()).toString(), t);
            }
        }
        
        pluginStatuses.put(plugin.description, Boolean.valueOf(status));
        return status;
    }
    
    @Override
    public void enablePlugins()
    {
        this.delegate.enablePlugins();
    }
    
    @Override
    public void detectPlugins(File folder)
    {
        this.delegate.detectPlugins(folder);
        for (File file : folder.listFiles())
        {
            if ((!(file.isFile())) || (!(file.getName().endsWith(".eclipseproject")))) //$NON-NLS-1$
                continue;
            final ProjectDescriptor prjDesc = this.getProjectDescription(file);
            if (prjDesc != null)
            {
                this.projectsToLoad.put(prjDesc.description.getName(), prjDesc);
            }
        }
    }
    
    @Override
    public <T extends Event> T callEvent(T event)
    {
        return this.delegate.callEvent(event);
    }
    
    @Override
    public void registerListener(Plugin plugin, Listener listener)
    {
        this.delegate.registerListener(plugin, listener);
    }
    
    @Override
    public void unregisterListener(Listener listener)
    {
        this.delegate.unregisterListener(listener);
    }
    
    @Override
    public void unregisterListeners(Plugin plugin)
    {
        this.delegate.unregisterListeners(plugin);
    }
    
    /**
     * helper class for project descriptors.
     */
    private static final class ProjectDescriptor
    {
        /** plugin description. */
        PluginDescription description;
        /** classpath. */
        URL[]             classpath;
    }
    
    /**
     * Fetched additional cloasses urls from properties
     * 
     * @param props
     * @return classes urls
     * @throws MalformedURLException
     */
    private URL[] fetchAdditionalUrlsFromProperties(Properties props) throws MalformedURLException
    {
        final List<URL> result = new ArrayList<>();
        if (props.containsKey("cpsize")) //$NON-NLS-1$
        {
            final int size = Integer.parseInt(props.getProperty("cpsize")); //$NON-NLS-1$
            for (int i = 0; i < size; i++)
            {
                final String type = props.getProperty("cptype" + i, "file"); //$NON-NLS-1$ //$NON-NLS-2$
                switch (type)
                {
                    case "file": //$NON-NLS-1$
                        result.add(new File(props.getProperty("cpfile" + i)).toURI().toURL()); //$NON-NLS-1$
                        break;
                    default:
                        // silently ignore
                        break;
                }
            }
        }
        return result.toArray(new URL[result.size()]);
    }
    
    /**
     * Reads properties from eclipse-project file.
     * 
     * @param file
     * @return properties.
     * @throws IOException
     */
    private Properties fetchProperties(File file) throws IOException
    {
        final Properties properties = new Properties();
        try (final InputStream is = new FileInputStream(file))
        {
            properties.load(is);
        }
        return properties;
    }
    
    /**
     * Fetches plugin descriptor from classes folder
     * 
     * @param file
     * @return plugin descriptor
     */
    private ProjectDescriptor getProjectDescription(File file)
    {
        Preconditions.checkNotNull(file, "File cannot be null"); //$NON-NLS-1$
        
        try
        {
            final Properties props = fetchProperties(file);
            final File classesDir = new File(props.getProperty("classes")); //$NON-NLS-1$
            
            final File bukkitYml = new File(classesDir, "bukkit.yml"); //$NON-NLS-1$
            final File pluginYml = bukkitYml.exists() ? bukkitYml : new File(classesDir, "plugin.yml"); //$NON-NLS-1$
            if (!pluginYml.exists())
            {
                throw new NullPointerException("Plugin must have a plugin.yml or bungee.yml"); //$NON-NLS-1$
            }
            try (final InputStream is = new FileInputStream(pluginYml))
            {
                PluginDescription desc = this.yaml.loadAs(is, PluginDescription.class);
                Preconditions.checkNotNull(desc.getName(), "Plugin from %s has no name", new Object[] { file }); //$NON-NLS-1$
                Preconditions.checkNotNull(desc.getMain(), "Plugin from %s has no main", new Object[] { file }); //$NON-NLS-1$
                desc.setFile(file);
                final URL[] urls = fetchAdditionalUrlsFromProperties(props);
                final URL[] cp = new URL[urls.length + 1];
                cp[0] = classesDir.toURI().toURL();
                if (urls.length > 0)
                {
                    System.arraycopy(urls, 0, cp, 1, urls.length);
                }
                final ProjectDescriptor pdesc = new ProjectDescriptor();
                pdesc.description = desc;
                pdesc.classpath = cp;
                return pdesc;
            }
        }
        catch (Exception ex)
        {
            ProxyServer.getInstance().getLogger().log(Level.WARNING, new StringBuilder().append("Could not load plugin from folder ").append(file).toString(), ex); //$NON-NLS-1$
        }
        return null;
    }
    
}
