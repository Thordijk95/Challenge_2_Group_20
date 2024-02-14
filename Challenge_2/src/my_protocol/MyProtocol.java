package my_protocol;

import framework.IRDTProtocol;
import framework.Utils;

import framework.Utils.Timeout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @version 10-07-2019
 *
 * Copyright University of Twente,  2013-2024
 *
 **************************************************************************
 *                          = Copyright notice =                          *
 *                                                                        *
 *            This file may ONLY  be distributed UNMODIFIED!              *
 * In particular, a correct solution to the challenge must  NOT be posted *
 * in public places, to preserve the learning effect for future students. *
 **************************************************************************
 */
public class MyProtocol extends IRDTProtocol {

    static final int SWS = 6;

    static final int RWS = 1;

    // change the following as you wish:
    static final int HEADERSIZE=1;   // number of header bytes in each packet
    static final int DATASIZE=128;   // max. number of user data bytes in each packet

    List<Integer> acknowledgedSegnums = new ArrayList<>();
    List<Integer[]> packetBuffer = new ArrayList<>();

    @Override
    public void sender() {
        System.out.println("Dit is onze code");
        System.out.println("Sending...");

        // read from the input file
        Integer[] fileContents = Utils.getFileContents(getFileID());
        // keep track of where we are in the data
        int filePointer = 0;
        int seqNum = 0;

        int LAF = -1;
        int LFS = -1;

        while (filePointer <= fileContents.length) {
            // create a new packet of appropriate size
            int datalen = Math.min(DATASIZE, fileContents.length - filePointer);
            Integer[] pkt = new Integer[HEADERSIZE + datalen];
            // write something random into the header byte
            if (filePointer + DATASIZE >= fileContents.length) {
                seqNum = -1;
            }

            pkt[0] = seqNum;
            // copy databytes from the input file into data part of the packet, i.e., after the header
            System.arraycopy(fileContents, filePointer, pkt, HEADERSIZE, datalen);

            sentPacket(pkt);

            LFS = seqNum;

            // and loop and sleep; you may use this loop to check for incoming acks...
            boolean stop = false;
            while (!stop) {
                try {
                    Thread.sleep(10);
                    Integer[] acknowledgePacket = getNetworkLayer().receivePacket();
                    if (acknowledgePacket != null && (LAF < acknowledgePacket[0] || LAF == SWS)
                        && acknowledgePacket[0] <= LFS) {
                        acknowledgedSegnums.add(acknowledgePacket[0]);
                        LAF = acknowledgePacket[0];
                        System.out.println("Acknowledge received: " + acknowledgePacket[0]);
                        stop = true;
                    }
                } catch (InterruptedException e) {
                    stop = true;
                }
            }
            filePointer = filePointer + DATASIZE;
            seqNum = seqNum < SWS ? seqNum + 1 : 0;
        }
    }

    @Override
    public void TimeoutElapsed(Object tag) {
        int z=(Integer)tag;
        // handle expiration of the timeout:
        System.out.println("this Timer expired with tag="+z);
        // check if package was acknowledged
        if (acknowledgedSegnums.contains((Integer)tag)) {
            acknowledgedSegnums.remove((Integer) tag);
        } else {
            // Resend the packet
            Integer[] retransmitPkt = findPacketSegNum((Integer) tag);
            sentPacket(retransmitPkt);
        }
    }

    @Override
    public Integer[] receiver() {
        System.out.println("Receiving...");

        Integer[] acknowledgePacket = new Integer[1];

        int LFR = -1;
        int LAF = LFR + RWS;

        // create the array that will contain the file contents
        // note: we don't know yet how large the file will be, so the easiest (but not most efficient)
        //   is to reallocate the array every time we find out there's more data
        Integer[] fileContents = new Integer[0];

        // loop until we are done receiving the file
        boolean stop = false;
        while (!stop) {
            // try to receive a packet from the network layer
            Integer[] packet = getNetworkLayer().receivePacket();

            // if we indeed received a packet
            if (packet != null) {
                // tell the user
                System.out.println("Received packet, length="+packet.length+"  first byte="+packet[0] );

                if (packet[0] <= LAF || packet[0] == 255) {
                    acknowledgePacket[0] = packet[0];
                    LFR = packet[0];
                    if (LAF < SWS) {
                        LAF = LFR + RWS;
                    } else { LAF = 0; }
                    // append the packet's data part (excluding the header) to the fileContents array, first making it larger
                    getNetworkLayer().sendPacket(acknowledgePacket);
                    System.out.println("Sent acknowledgment");
                    int oldlength=fileContents.length;
                    int datalen= packet.length - HEADERSIZE;
                    fileContents = Arrays.copyOf(fileContents, oldlength+datalen);
                    System.arraycopy(packet, HEADERSIZE, fileContents, oldlength, datalen);
                    if (packet[0] == 255) {
                        stop = true;
                    }
                }
            }else{
                // wait ~10ms (or however long the OS makes us wait) before trying again
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    stop = true;
                }
            }
        }
        // return the output file
        return fileContents;
    }

    public void sentPacket(Integer[] pkt) {
        // send the packet to the network layer
        getNetworkLayer().sendPacket(pkt);
        if (!packetBuffer.contains(pkt)) {
            packetBuffer.add(pkt);
        }
        System.out.println("Sent one packet with header=" + pkt[0]);

        // schedule a timer for 1000 ms into the future, just to show how that works:
        framework.Utils.Timeout.SetTimeout(1250, this, pkt[0]);
    }

    public Integer[] findPacketSegNum(int segNum) {
        for (int i = 0; i < packetBuffer.size(); i++) {
            if (packetBuffer.get(i)[0] == segNum) {
                return packetBuffer.get(i);
            }
        }
        return null;
    }
}
