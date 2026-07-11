/**
 * Copyright 2014 Ryszard Wiśniewski <brut.alll@gmail.com>
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bingbaihanji.fxdecomplie.core.jadx.core.utils.android;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author Ryszard Wiśniewski "brut.alll@gmail.com"
 */
public abstract class DataInputDelegate implements DataInput {
    protected final DataInput mDelegate;

    public DataInputDelegate(DataInput delegate) {
        this.mDelegate = delegate;
    }

    @Override
    public int skipBytes(int n) throws IOException {
        return mDelegate.skipBytes(n);
    }

    @Override
    public int readUnsignedShort() throws IOException {
        return mDelegate.readUnsignedShort();
    }

    @Override
    public int readUnsignedByte() throws IOException {
        return mDelegate.readUnsignedByte();
    }

    @Override
    public String readUTF() throws IOException {
        return mDelegate.readUTF();
    }

    @Override
    public short readShort() throws IOException {
        return mDelegate.readShort();
    }

    @Override
    public long readLong() throws IOException {
        return mDelegate.readLong();
    }

    @Override
    public String readLine() throws IOException {
        return mDelegate.readLine();
    }

    @Override
    public int readInt() throws IOException {
        return mDelegate.readInt();
    }

    @Override
    public void readFully(byte[] b, int off, int len) throws IOException {
        mDelegate.readFully(b, off, len);
    }

    @Override
    public void readFully(byte[] b) throws IOException {
        mDelegate.readFully(b);
    }

    @Override
    public float readFloat() throws IOException {
        return mDelegate.readFloat();
    }

    @Override
    public double readDouble() throws IOException {
        return mDelegate.readDouble();
    }

    @Override
    public char readChar() throws IOException {
        return mDelegate.readChar();
    }

    @Override
    public byte readByte() throws IOException {
        return mDelegate.readByte();
    }

    @Override
    public boolean readBoolean() throws IOException {
        return mDelegate.readBoolean();
    }
}
