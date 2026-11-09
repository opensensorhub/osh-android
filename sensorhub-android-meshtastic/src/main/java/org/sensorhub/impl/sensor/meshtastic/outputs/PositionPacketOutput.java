package org.sensorhub.impl.sensor.meshtastic.outputs;

import com.google.protobuf.InvalidProtocolBufferException;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;

import org.meshtastic.proto.MeshProtos;
import org.meshtastic.proto.Portnums;
import org.sensorhub.api.data.DataEvent;
import org.sensorhub.impl.sensor.meshtastic.MeshtasticSensor;
import org.vast.data.TextEncodingImpl;
import org.vast.swe.helper.GeoPosHelper;

public class PositionPacketOutput extends AbstractPacketOutput {

    protected static final String NAME = "position";
    DataComponent recordDescription;
    DataEncoding recordEncoding;

    public PositionPacketOutput(MeshtasticSensor parentSensor) {
        super(NAME, parentSensor);

        GeoPosHelper fac = new GeoPosHelper();

        recordDescription = fac.createRecord()
                .name(getName())
                .addField("samplingTime", fac.createTime().asSamplingTimeIsoUTC())
                .addField("fromId", fac.createCount())
                .addField("toId", fac.createCount())
                .addField("location", fac.createLocationVectorLLA())
                .build();

        recordEncoding = new TextEncodingImpl();
    }

    @Override
    public boolean canHandlePacket(MeshProtos.MeshPacket packet) {
        return packet.hasDecoded() && packet.getDecoded().getPortnum() == Portnums.PortNum.POSITION_APP;
    }

    @Override
    public void onPacketMessage(MeshProtos.MeshPacket packet) {
        MeshProtos.Data decoded = packet.getDecoded();

        try {
            MeshProtos.Position position = MeshProtos.Position.parseFrom(decoded.getPayload());
            double lat = position.getLatitudeI() * 1e-7;
            double lon = position.getLongitudeI() * 1e-7;
            double alt = position.getAltitude();

            DataBlock dataBlock = latestRecord == null ? recordDescription.createDataBlock() : latestRecord.renew();

            int i = 0;
            dataBlock.setDoubleValue(i++, System.currentTimeMillis()/1000d);
            dataBlock.setIntValue(i++, packet.getFrom());
            dataBlock.setIntValue(i++, packet.getTo());
            dataBlock.setDoubleValue(i++, lat);
            dataBlock.setDoubleValue(i++, lon);
            dataBlock.setDoubleValue(i, alt);

            eventHandler.publish(new DataEvent(System.currentTimeMillis(), this, dataBlock));
        } catch (InvalidProtocolBufferException e) {
            getLogger().error("Unable to decode position");
        }
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
