/*  Copyright (C) 2024-2026 José Rebelo, Thomas Kuehne

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;

import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiSmartProto.Smart;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.communicator.CobsCoDec;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.FitImporter;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.messages.GFDIMessage;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

public class Development {
    @Test
    @Ignore("helper test for development, remove this while debugging")
    public void decodeProtobuf() throws Exception {
        var bytes = java.util.Base64.getDecoder().decode("sgEQCg4IAhACIAoqBAgCEAAwCg==");
        // var bytes = com.google.protobuf.ByteString.copyFromUtf8("\262\001\020\n\016\b\002\020\002 \n*\004\b\002\020\0000\n");
        // var bytes = GB.hexStringToByteArray("b201100a0e08021002200a2a0408021000300a");

        Smart smart = Smart.parseFrom(bytes);
        String dump = smart.toString();
        Assert.fail(dump);
    }

    @Test
    @Ignore("helper test for development, remove this while debugging")
    public void decodeGFDIMessage() {
        var bytes = GB.hexStringToByteArray("0A008813A21300010889");
        GFDIMessage message = GFDIMessage.parseIncoming(bytes);
        String dump = message.toString();
        Assert.assertTrue(dump, false);
    }

    @Test
    @Ignore("helper test for development, remove this while debugging")
    public void decodeCobs() {
        final CobsCoDec cobsCoDec = new CobsCoDec();
        var bytes = GB.hexStringToByteArray("00020c0baa1380a4bd796705196600");
        cobsCoDec.receivedBytes(bytes);
        final byte[] deCobs = cobsCoDec.retrieveMessage();
        final GFDIMessage gfdiMessage = GFDIMessage.parseIncoming(deCobs);
    }

    @Test
    @Ignore("helper test for development, remove this while debugging")
    public void decodeFitFromLocalTestFile() throws Exception {
        final FitImporter fitImporter = new FitImporter(null, null);
        fitImporter.importFile(new File("/storage/SKIN_TEMP.fit"), false);
    }

    @Test
    @Ignore("helper test for development, remove this while debugging")
    public void decodeFitFromLocalTestFolder() throws Exception {
        final File dir = new File("/storage/MONITOR/2026/");
        final File[] files = dir.listFiles();
        Assert.assertNotNull(files);
        for (File file : files) {
            if (!file.getName().endsWith(".fit")) {
                continue;
            }
            final FitImporter fitImporter = new FitImporter(null, null);
            fitImporter.importFile(file, false);
        }
    }
}
