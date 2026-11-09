package org.sensorhub.impl.sensor.meshtastic.outputs;


import org.meshtastic.proto.MeshProtos;
import org.sensorhub.impl.sensor.meshtastic.MeshtasticSensor;

public abstract class AbstractPacketOutput extends AbstractMeshtasticOutput {

    public AbstractPacketOutput(String name, MeshtasticSensor parentSensor) {
        super(name, parentSensor);
    }

    public abstract boolean canHandlePacket(MeshProtos.MeshPacket packet);

    public abstract void onPacketMessage(MeshProtos.MeshPacket packet);

    @Override
    public void onMessage(MeshProtos.FromRadio msg) {
        onPacketMessage(msg.getPacket());
    }

    @Override
    public boolean canHandle(MeshProtos.FromRadio msg) {
        return msg.hasPacket() && canHandlePacket(msg.getPacket());
    }

}
