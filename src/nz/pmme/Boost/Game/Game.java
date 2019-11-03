package nz.pmme.Boost.Game;

import nz.pmme.Boost.Config.GameConfig;
import nz.pmme.Boost.Config.Messages;
import nz.pmme.Boost.Enums.GameState;
import nz.pmme.Boost.Main;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class Game
{
    private Main plugin;
    private GameConfig gameConfig;
    private GameState gameState;
    private Map< UUID, PlayerInfo > players = new HashMap<>();

    private int remainingQueueTime;
    private BukkitTask queueTask;

    public Game( Main plugin, GameConfig gameConfig )
    {
        this.plugin = plugin;
        this.gameConfig = gameConfig;
        this.gameState = GameState.STOPPED;

        if( this.gameConfig.isAutoQueue() ) this.startQueuing();
    }

    public GameConfig getGameConfig() {
        return gameConfig;
    }

    public int getPlayerCount() {
        return players.size();
    }

    public int getRemainingQueueTime() {
        return remainingQueueTime;
    }

    public void startQueuing()
    {
        gameState = GameState.QUEUING;
        remainingQueueTime = gameConfig.getCountdown();
        BukkitRunnable queueRunnable = new BukkitRunnable()
        {
            @Override
            public void run() {
                if( !plugin.isBoostEnabled() ) return;
                if( getPlayerCount() < gameConfig.getMinPlayers() ) {
                    remainingQueueTime = gameConfig.getCountdown();
                } else {
                    if( remainingQueueTime <= 0 ) {
                        this.cancel();
                        Game.this.start();
                    } else {
                        if( remainingQueueTime % gameConfig.getCountdownAnnounceTime() == 0 || remainingQueueTime <= 5 ) {
                            String message = plugin.getLoadedConfig().getMessage( Messages.GAME_COUNTDOWN ).replaceAll( "%time%", String.valueOf( remainingQueueTime ) ).replaceAll( "%game%", gameConfig.getDisplayName() );
                            for( PlayerInfo playerInfo : players.values() ) {
                                playerInfo.getPlayer().sendMessage( message );
                            }
                        }
                        --remainingQueueTime;
                    }
                }
            }
        };
        queueTask = queueRunnable.runTaskTimer( plugin, 1L, 20L/*ticksPerSecond*/ );
    }

    public boolean join( Player player )
    {
        if( gameState != GameState.QUEUING )
        {
            switch( gameState )
            {
                case RUNNING:
                    player.sendMessage( plugin.getLoadedConfig().getMessage( Messages.NO_JOIN_GAME_RUNNING ).replaceAll( "%game%", gameConfig.getDisplayName() ) );
                    break;
                case STOPPED:
                    player.sendMessage( plugin.getLoadedConfig().getMessage( Messages.NO_JOIN_GAME_STOPPED ).replaceAll( "%game%", gameConfig.getDisplayName() ) );
                    break;
            }
            return true;
        }
        if( gameConfig.getMaxPlayers() > 0 && this.getPlayerCount() >= gameConfig.getMaxPlayers() )
        {
            player.sendMessage( plugin.getLoadedConfig().getMessage( Messages.NO_JOIN_GAME_FULL ).replaceAll( "%game%", gameConfig.getDisplayName() ) );
        }
        players.put( player.getUniqueId(), new PlayerInfo( player ) );
        player.teleport( gameConfig.getLobbySpawn() );
        player.setGameMode( GameMode.ADVENTURE );
        player.sendMessage( plugin.getLoadedConfig().getMessage( Messages.JOIN_GAME ).replaceAll( "%game%", gameConfig.getDisplayName() ) );
        return true;
    }

    public void leave( Player player )
    {
        players.remove( player.getUniqueId() );
        player.teleport( gameConfig.getLobbySpawn() );
        player.setGameMode( GameMode.ADVENTURE );
        player.sendMessage( plugin.getLoadedConfig().getMessage( Messages.LEAVE_GAME ).replaceAll( "%game%", gameConfig.getDisplayName() ) );
        player.getInventory().clear();

        if( gameState == GameState.RUNNING )
        {
            List<Player> activePlayers = getActivePlayerList();
            if( activePlayers.size() <= 1 )
            {
                gameState = GameState.STOPPED;
                if( activePlayers.size() == 1 )
                {
                    String message = plugin.getLoadedConfig().getMessage( Messages.WINNER ).replaceAll( "%player%", activePlayers.get( 0 ).getDisplayName() ).replaceAll( "%game%", gameConfig.getDisplayName() );
                    for( PlayerInfo playerInfo : players.values() ) {
                        playerInfo.getPlayer().sendMessage( message );
                    }
                }
                this.end( false );
            }
        }
    }

    public List<Player> getPlayerList()
    {
        List<Player> playerList = new ArrayList<>();
        for( PlayerInfo playerInfo : players.values() )
        {
            playerList.add( playerInfo.getPlayer() );
        }
        return playerList;
    }

    public List<Player> getActivePlayerList()
    {
        List<Player> playerList = new ArrayList<>();
        for( PlayerInfo playerInfo : players.values() )
        {
            if( playerInfo.isActive() )
            {
                playerList.add( playerInfo.getPlayer() );
            }
        }
        return playerList;
    }

    public void setPlayerLost( Player player )
    {
        if( gameState == GameState.RUNNING )
        {
            PlayerInfo payerInfoLost = players.get( player.getUniqueId() );
            if( payerInfoLost != null ) payerInfoLost.setLost();
            player.teleport( gameConfig.getLossSpawn() );
            player.setGameMode( GameMode.SPECTATOR );
            player.sendMessage( plugin.getLoadedConfig().getMessage( Messages.LOST ).replaceAll( "%game%", gameConfig.getDisplayName() ) );
            player.getInventory().clear();

            List<Player> activePlayers = getActivePlayerList();
            if( activePlayers.size() <= 1 )
            {
                gameState = GameState.STOPPED;
                if( activePlayers.size() == 1 )
                {
                    String message = plugin.getLoadedConfig().getMessage( Messages.WINNER ).replaceAll( "%player%", activePlayers.get( 0 ).getDisplayName() ).replaceAll( "%game%", gameConfig.getDisplayName() );
                    for( PlayerInfo playerInfo : players.values() ) {
                        playerInfo.getPlayer().sendMessage( message );
                    }
                }
                this.end( false );
            }
        }
    }

    public boolean isActiveInGame( Player player )
    {
        PlayerInfo playerInfo = players.get( player.getUniqueId() );
        if( playerInfo == null ) return false;
        return playerInfo.isActive();
    }

    public String getPlayerStateText( Player player )
    {
        PlayerInfo playerInfo = players.get( player.getUniqueId() );
        if( playerInfo == null ) return "Not in game";
        return playerInfo.getPlayerStateText();
    }

    public boolean isRunning()
    {
        return gameState == GameState.RUNNING;
    }

    public boolean isQueuing()
    {
        return gameState == GameState.QUEUING;
    }

    public String getGameStateText()
    {
        return gameState.toString();
    }

    public boolean start()
    {
        if( queueTask != null && !queueTask.isCancelled() ) queueTask.cancel();
        gameState = GameState.RUNNING;
        for( PlayerInfo playerInfo : players.values() )
        {
            // TODO: Probably worth making PlayerInfo a PlayerControl class, then adding startGame() to it. Needs spawn point and spread.
            Location location = new Location( gameConfig.getStartSpawn().getWorld(), gameConfig.getStartSpawn().getX(), gameConfig.getStartSpawn().getY(), gameConfig.getStartSpawn().getZ(), (float)( Math.random() * 360.0 ), 0 );
            Vector spreadVector = new Vector( Math.random()*gameConfig.getSpawnSpread(), 0, 0 ).rotateAroundY( Math.random() * 360.0 );
            location.add( spreadVector );
            playerInfo.getPlayer().teleport( location );
            playerInfo.getPlayer().setGameMode( GameMode.ADVENTURE );
            playerInfo.getPlayer().sendMessage( plugin.getLoadedConfig().getMessage( Messages.GAME_STARTED ).replaceAll( "%game%", gameConfig.getDisplayName() ) );
            playerInfo.getPlayer().getInventory().setItemInMainHand( new ItemStack( Material.DIAMOND_HOE ) );
            playerInfo.setActive();
        }
        return true;
    }

    public boolean end( boolean noAutoQueue )
    {
        boolean wasQueuing = ( gameState == GameState.QUEUING || ( queueTask != null && !queueTask.isCancelled() ) );
        if( queueTask != null && !queueTask.isCancelled() ) queueTask.cancel();  // Cancel the queue in case it's queuing rather than running.
        gameState = GameState.STOPPED;
        for( PlayerInfo playerInfo : players.values() )
        {
            playerInfo.getPlayer().teleport( gameConfig.getLobbySpawn() );
            playerInfo.getPlayer().setGameMode( GameMode.ADVENTURE );
            playerInfo.getPlayer().sendMessage( plugin.getLoadedConfig().getMessage( Messages.GAME_ENDED ).replaceAll( "%game%", gameConfig.getDisplayName() ) );
            playerInfo.getPlayer().getInventory().clear();
            plugin.getGameManager().removePlayer( playerInfo.getPlayer() );
        }
        players.clear();
        if( gameConfig.isAutoQueue() && !wasQueuing && !noAutoQueue ) this.startQueuing();    // Don't restart queuing if we were in the queuing state when end was called.
        return true;
    }
}