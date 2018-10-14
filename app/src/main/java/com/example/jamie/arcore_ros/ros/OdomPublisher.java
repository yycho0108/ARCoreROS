package com.example.jamie.arcore_ros.ros;

import org.ros.node.ConnectedNode;
import org.ros.node.topic.Publisher;

import geometry_msgs.Pose;
import nav_msgs.Odometry;

public class OdomPublisher {
    private final Publisher<Odometry> publisher;
    private Odometry msg;
    private boolean updated;

    public OdomPublisher(final ConnectedNode connectedNode) {
        this.publisher = connectedNode.newPublisher("android/odom", Odometry._TYPE);
        this.msg = publisher.newMessage();
        initialize();
    }

    private void initialize(){
        Pose p = msg.getPose().getPose();

        p.getPosition().setX(0.0);
        p.getPosition().setY(0.0);
        p.getPosition().setZ(0.0);

        p.getOrientation().setX(0.0);
        p.getOrientation().setY(0.0);
        p.getOrientation().setZ(0.0);
        p.getOrientation().setW(1.0);

        updated = false;
    }

    public void update(float[] txn, float[] rxn) {
        // rxn formatted {x,y,z,w}

        updated = true;
        Pose p = msg.getPose().getPose();

        p.getPosition().setX(txn[0]);
        p.getPosition().setY(txn[1]);
        p.getPosition().setZ(txn[2]);

        p.getOrientation().setX(rxn[0]);
        p.getOrientation().setY(rxn[1]);
        p.getOrientation().setZ(rxn[2]);
        p.getOrientation().setW(rxn[3]);

        //updateCovariance(??);
    }


    public void updateCovariance(double a){
        msg.getPose().setCovariance(new double[]{

               }
        );
    }

    public void publish() {
        //only publish when data got updated
        if(updated){
            updated = false;
            Utilities.setHeader(msg.getHeader(), "odom"); // populate header
            msg.setChildFrameId("android");
            publisher.publish(msg);
        }
    }
}
