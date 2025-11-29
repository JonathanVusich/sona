package org.sona.format;

import lombok.Value;
import org.apache.tomcat.util.buf.HexUtils;

@Value
public class MD5Checksum {

    byte[] checksum;

    @Override
    public String toString() {
        return HexUtils.toHexString(checksum);
    }
}
