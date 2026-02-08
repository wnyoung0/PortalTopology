package org.wyoung.portaltopology;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.*;
@Mod(Portaltopology.MODID)

public class Portaltopology {
    public static final String MODID = "portaltopology";
    private static final Logger LOGGER = LogUtils.getLogger();
    public Portaltopology(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(this);
    }
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }

    public static int[] dx = new int[] {0,0,1,-1};
    public static int[] dy = new int[] {1,-1,0,0};


    public static BlockPos getPBPos(int px,int py,int plane,int fl){
        if(fl==0){
            return new BlockPos(px,py,plane);
        }
        else{
            return new BlockPos(plane,px,py);
        }
    }
    @Nullable
    public static List<Pbfs> foundArea(BlockPos start,Direction.Axis axis,Level level){

        List<Pbfs> ret = new ArrayList<Pbfs>();
        Set<String> visited = new HashSet<>();

        int sx= start.getX();
        int sy= start.getY();
        int sz= start.getZ();
        int plane;
        int fl=0;
        Queue<Pbfs> q = new LinkedList<Pbfs>();
        if(axis==Direction.Axis.X){
            fl=1;

            q.offer(new Pbfs(sy,sz));
            plane=sx;

        }
        else{
            q.offer(new Pbfs(sx,sy));
            plane=sz;
        }

        visited.add(String.valueOf(q.peek().x) + "," + String.valueOf(q.peek().y));
        int limit=0;

        while(!q.isEmpty()){
            if(limit>525){
                break;
            }
            limit+=1;

            Pbfs now = q.poll();
            ret.add(now);
            int nx=now.x;
            int ny=now.y;

            for(int i=0;i<4;i++){
                int tx=nx+dx[i];
                int ty=ny+dy[i];
                Pbfs tP = new Pbfs(tx,ty);

                if (visited.contains(String.valueOf(tx) + "," + String.valueOf(ty))){continue;}//visited

                visited.add(String.valueOf(tx) + "," + String.valueOf(ty));

                if((fl==0 && (ty<level.getMinY() || ty>level.getMaxY()))||(fl==1&&(tx<level.getMinY() || tx>level.getMaxY()))){
                    continue;
                }//out of range

                BlockPos tPos = getPBPos(tx,ty,plane,fl);
                BlockState tState = level.getBlockState(tPos);

                if(tState.is(Blocks.AIR) || tState.is(Blocks.FIRE)){
                    q.add(new Pbfs(tx,ty));
                    continue;
                }//can be portalBlock

                else if(tState.is(Blocks.OBSIDIAN)){
                    continue;
                }//Okay to meet
                else{
                    visited=null;
                    q=null;
                    ret=null;
                    return null;
                }

            }
        }
        visited=null;
        q=null;
        return ret;
    }

    public static boolean fillPortal(List<Pbfs> lp,Level level,BlockState portal,int plane,int fl){
        Set<String> reserved = new HashSet<>();
        if (lp!=null) {
            for(Pbfs pbfs : lp){
                reserved.add(String.valueOf(pbfs.x)+','+String.valueOf(pbfs.y));
            }

            for (Pbfs pbfs : lp) {
                for(int i=0;i<4;i++){
                    int tx=pbfs.x+dx[i];
                    int ty=pbfs.y+dy[i];

                    if((fl==0 && (ty<level.getMinY() || ty>level.getMaxY()))||(fl==1&&(tx<level.getMinY() || tx>level.getMaxY()))){
                        lp=null;
                     //   LOGGER.info("outOfRange");
                        return false;
                    }//out of range
                    if((!reserved.contains(String.valueOf(tx)+','+String.valueOf(ty))) &&
                            !level.getBlockState(new BlockPos(getPBPos(tx,ty,plane,fl))).is(Blocks.OBSIDIAN)){
                        lp=null;
                      //  LOGGER.info("notClosed");
                        return false;
                    }//not closed
                }
            }
        }

        while (lp != null && !lp.isEmpty()) {
            Pbfs now = lp.removeFirst();
         //   LOGGER.info(String.valueOf(now.x)+'|'+String.valueOf(now.y)+'|'+String.valueOf(plane));
            level.setBlock(getPBPos(now.x, now.y, plane, fl), portal, 3);
        }//place
        return true;
    }

    @EventBusSubscriber(modid = MODID)
    public static class PortalEvent {
        @SubscribeEvent
        public static void onIgnition(PlayerInteractEvent.RightClickBlock event){

            BlockPos pos = event.getPos();
            Player player = event.getEntity();
            Direction face = event.getFace();
            Level level = event.getLevel();
            BlockState block = level.getBlockState(pos);

            if(block.is(Blocks.OBSIDIAN) && (face==Direction.UP) && (player.getMainHandItem().is(Items.FLINT_AND_STEEL))) {
             //   LOGGER.info("ye");
                try {
                    int y = pos.getY() + 1;
                    BlockPos start = new BlockPos(pos.getX(), y, pos.getZ());
                    int fl = 1;
                    int plane = pos.getX();
                    List<Pbfs> lp = foundArea(start, Direction.Axis.X, level);
                    BlockState portal;
                    if (lp == null) {
                        portal = Blocks.NETHER_PORTAL
                                .defaultBlockState()
                                .setValue(NetherPortalBlock.AXIS, Direction.Axis.X);
                        fl = 0;
                        plane = pos.getZ();
                        lp = foundArea(start, Direction.Axis.Z, level);

                    } else {
                        portal = Blocks.NETHER_PORTAL
                                .defaultBlockState()
                                .setValue(NetherPortalBlock.AXIS, Direction.Axis.Z);
                    }

                //    LOGGER.info(String.valueOf(fl));
                 //   if(lp==null){LOGGER.info("null");}
                 //   else{LOGGER.info("notNull");}

                    if(!fillPortal(lp,level,portal,plane,fl) && fl==1){
                        portal = Blocks.NETHER_PORTAL
                                .defaultBlockState()
                                .setValue(NetherPortalBlock.AXIS, Direction.Axis.X);
                        fl = 0;
                        plane = pos.getZ();
                        lp = foundArea(start, Direction.Axis.Z, level);
                        fillPortal(lp,level,portal,plane,fl);
                    }

                    lp=null;
                } catch (Exception e) {
                    LOGGER.info("error"+e.getMessage());
                }

            }

        }
    }
}
