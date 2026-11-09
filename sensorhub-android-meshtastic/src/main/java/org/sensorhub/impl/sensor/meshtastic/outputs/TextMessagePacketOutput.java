package org.sensorhub.impl.sensor.meshtastic.outputs;


import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;

import org.meshtastic.proto.MeshProtos;
import org.meshtastic.proto.Portnums;
import org.sensorhub.api.data.DataEvent;
import org.sensorhub.impl.sensor.meshtastic.MeshtasticSensor;
import org.vast.data.TextEncodingImpl;

public class TextMessagePacketOutput extends AbstractPacketOutput {

    public static final String NAME = "textMessage";

    DataComponent recordDescription;
    DataEncoding recordEncoding;

    public TextMessagePacketOutput(MeshtasticSensor parentSensor) {
        super(NAME, parentSensor);

        recordDescription = fac.createRecord()
                .name(getName())
                .addField("samplingTime", fac.createTime().asSamplingTimeIsoUTC())
                .addField("fromId", fac.createCount())
                .addField("toId", fac.createCount())
                .addField("message", fac.createText())
                .build();

        recordEncoding = new TextEncodingImpl();
    }

    @Override
    public boolean canHandlePacket(MeshProtos.MeshPacket packet) {
        return packet.hasDecoded() && packet.getDecoded().getPortnum() == Portnums.PortNum.TEXT_MESSAGE_APP;
    }

    @Override
    public void onPacketMessage(MeshProtos.MeshPacket packet) {
        MeshProtos.Data decoded = packet.getDecoded();
        String message = decoded.getPayload().toStringUtf8();

        DataBlock dataBlock = latestRecord == null ? recordDescription.createDataBlock() : latestRecord.renew();

        int i = 0;
        dataBlock.setDoubleValue(i++, System.currentTimeMillis()/1000d);
        dataBlock.setIntValue(i++, packet.getFrom());
        dataBlock.setIntValue(i++, packet.getTo());
        dataBlock.setStringValue(i, message);

        eventHandler.publish(new DataEvent(System.currentTimeMillis(), this, dataBlock));
    }

    @Override
    public DataComponent getRecordDescription() {
        return recordDescription;
    }

    @Override
    public DataEncoding getRecommendedEncoding() {
        return recordEncoding;
    }
}
