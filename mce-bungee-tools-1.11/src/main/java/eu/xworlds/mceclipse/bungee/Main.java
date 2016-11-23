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

import java.lang.reflect.Field;
import java.security.Security;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.md_5.bungee.Bootstrap;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.command.ConsoleCommandSender;

/**
 * Main starter for bungee.
 * 
 * @author mepeisen
 */
public class Main extends Bootstrap
{
    
    /**
     * @param args
     */
    public static void main(String[] args) throws Exception
    {
        Security.setProperty("networkaddress.cache.ttl", "30"); //$NON-NLS-1$ //$NON-NLS-2$
        Security.setProperty("networkaddress.cache.negative.ttl", "10"); //$NON-NLS-1$ //$NON-NLS-2$
        
        OptionParser parser = new OptionParser();
        parser.allowsUnrecognizedOptions();
        parser.acceptsAll(Arrays.asList(new String[] { "v", "version" })); //$NON-NLS-1$ //$NON-NLS-2$
        parser.acceptsAll(Arrays.asList(new String[] { "noconsole" })); //$NON-NLS-1$
        
        OptionSet options = parser.parse(args);
        
        if (options.has("version")) //$NON-NLS-1$
        {
            System.out.println(Bootstrap.class.getPackage().getImplementationVersion());
            return;
        }
        
        if ((BungeeCord.class.getPackage().getSpecificationVersion() != null) && (System.getProperty("IReallyKnowWhatIAmDoingISwear") == null)) //$NON-NLS-1$
        {
            Date buildDate = new SimpleDateFormat("yyyyMMdd").parse(BungeeCord.class.getPackage().getSpecificationVersion()); //$NON-NLS-1$
            
            Calendar deadline = Calendar.getInstance();
            deadline.add(3, -4);
            if (buildDate.before(deadline.getTime()))
            {
                System.err.println("*** Warning, this build is outdated ***"); //$NON-NLS-1$
                System.err.println("*** Please download a new build from http://ci.md-5.net/job/BungeeCord ***"); //$NON-NLS-1$
                System.err.println("*** You will get NO support regarding this build ***"); //$NON-NLS-1$
                System.err.println("*** Server will start in 10 seconds ***"); //$NON-NLS-1$
                Thread.sleep(TimeUnit.SECONDS.toMillis(10L));
            }
        }
        
        BungeeCord bungee = new BungeeCord();
        ProxyServer.setInstance(bungee);
        final Field field = BungeeCord.class.getDeclaredField("pluginManager"); //$NON-NLS-1$
        field.setAccessible(true);
        field.set(bungee, new ExtendedPluginManager(bungee));
        bungee.getLogger().info("Enabled BungeeCord version " + bungee.getVersion()); //$NON-NLS-1$
        bungee.start();
        
        String line;
        while ((!(options.has("noconsole"))) && (bungee.isRunning) && ((line = bungee.getConsoleReader().readLine(">")) != null)) //$NON-NLS-1$ //$NON-NLS-2$
        {
            if (bungee.getPluginManager().dispatchCommand(ConsoleCommandSender.getInstance(), line))
                continue;
            bungee.getConsole().sendMessage(ChatColor.RED + "Command not found"); //$NON-NLS-1$
        }
    }
    
}
