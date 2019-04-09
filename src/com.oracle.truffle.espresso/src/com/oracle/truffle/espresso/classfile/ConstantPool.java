/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.espresso.classfile;

import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.CLASS;
import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.DOUBLE;
import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.FIELD_REF;
import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.FLOAT;
import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.INTEGER;
import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.INTERFACE_METHOD_REF;
import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.INVOKEDYNAMIC;
import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.LONG;
import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.METHOD_REF;
import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.NAME_AND_TYPE;
import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.STRING;
import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.UTF8;

import java.util.Arrays;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Constant;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;
import com.oracle.truffle.object.DebugCounter;

/**
 * Immutable, shareable constant-pool representation.
 */
public abstract class ConstantPool {

    static final DebugCounter utf8EntryCount = DebugCounter.create("utf8EntryCount");

    public enum Tag {
        INVALID(0),
        UTF8(1),
        INTEGER(3),
        FLOAT(4),
        LONG(5),
        DOUBLE(6),
        CLASS(7),
        STRING(8),
        FIELD_REF(9),
        METHOD_REF(10),
        INTERFACE_METHOD_REF(11),
        NAME_AND_TYPE(12),
        METHODHANDLE(15),
        METHODTYPE(16),
        DYNAMIC(17),
        INVOKEDYNAMIC(18),
        MODULE(19),
        PACKAGE(20);

        private final byte value;

        Tag(int value) {
            assert (byte) value == value;
            this.value = (byte) value;
        }

        public final int getValue() {
            return value;
        }

        static Tag fromValue(int value) {
            // @formatter:off
            switch (value) {
                case 1: return UTF8;
                case 3: return INTEGER;
                case 4: return FLOAT;
                case 5: return LONG;
                case 6: return DOUBLE;
                case 7: return CLASS;
                case 8: return STRING;
                case 9: return FIELD_REF;
                case 10: return METHOD_REF;
                case 11: return INTERFACE_METHOD_REF;
                case 12: return NAME_AND_TYPE;
                case 15: return METHODHANDLE;
                case 16: return METHODTYPE;
                case 17: return DYNAMIC;
                case 18: return INVOKEDYNAMIC;
                case 19: return MODULE;
                case 20: return PACKAGE;
                default: return null;
            }
            // @formatter:on
        }

        public final static List<Tag> VALUES = Collections.unmodifiableList(Arrays.asList(values()));
    }

    public abstract int length();

    public abstract PoolConstant at(int index, String description);

    public final PoolConstant at(int index) {
        return at(index, null);
    }

    static ClassFormatError unexpectedEntry(int index, ConstantPool.Tag tag, String description, ConstantPool.Tag... expected) {
        CompilerDirectives.transferToInterpreter();
        throw verifyError("Constant pool entry" + (description == null ? "" : " for " + description) + " at " + index + " is a " + tag + ", expected " + Arrays.toString(expected));
    }

    public final ClassFormatError unexpectedEntry(int index, String description, ConstantPool.Tag... expected) {
        CompilerDirectives.transferToInterpreter();
        throw unexpectedEntry(index, tagAt(index), description, expected);
    }

    static VerifyError verifyError(String message) {
        CompilerDirectives.transferToInterpreter();
        throw new VerifyError(message);
    }

    /**
     * Gets the tag at a given index. If {@code index == 0} or there is no valid entry at
     * {@code index} (e.g. it denotes the slot following a double or long entry), then
     * {@link ConstantPool.Tag#INVALID} is returned.
     */
    public final Tag tagAt(int index) {
        return at(index).tag();
    }

    public final int intAt(int index) {
        return intAt(index, null);
    }

    public final int intAt(int index, String description) {
        try {
            final IntegerConstant constant = (IntegerConstant) at(index);
            return constant.value();
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, description, INTEGER);
        }
    }

    public final long longAt(int index) {
        return longAt(index, null);
    }

    public final long longAt(int index, String description) {
        try {
            final LongConstant constant = (LongConstant) at(index);
            return constant.value();
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, description, LONG);
        }
    }

    public final float floatAt(int index) {
        return floatAt(index, null);
    }

    public final float floatAt(int index, String description) {
        try {
            final FloatConstant constant = (FloatConstant) at(index);
            return constant.value();
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, description, FLOAT);
        }
    }

    public final double doubleAt(int index) {
        return doubleAt(index, null);
    }

    public final double doubleAt(int index, String description) {
        try {
            final DoubleConstant constant = (DoubleConstant) at(index);
            return constant.value();
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, description, DOUBLE);
        }
    }

    public final <T> Symbol<T> utf8At(int index) {
        return utf8At(index, null);
    }

    @SuppressWarnings("unchecked")
    public final <T> Symbol<T> utf8At(int index, String description) {
        try {
            final Utf8Constant constant = (Utf8Constant) at(index);
            return (Symbol<T>) constant.value();
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, description, UTF8);
        }
    }

    public final Symbol<Constant> stringAt(int index) {
        return stringAt(index, null);
    }

    public final Symbol<Constant> stringAt(int index, String description) {
        try {
            final StringConstant constant = (StringConstant) at(index);
            return constant.getSymbol(this);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, description, STRING);
        }
    }

    // region unresolved constants

    public final NameAndTypeConstant nameAndTypeAt(int index) {
        return nameAndTypeAt(index, null);
    }

    public final NameAndTypeConstant nameAndTypeAt(int index, String description) {
        try {
            return (NameAndTypeConstant) at(index);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, description, NAME_AND_TYPE);
        }
    }

    public final ClassConstant classAt(int index) {
        return classAt(index, null);
    }

    public final ClassConstant classAt(int index, String description) {
        try {
            return (ClassConstant) at(index);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, description, CLASS);
        }
    }

    public final MemberRefConstant memberAt(int index) {
        return memberAt(index, null);
    }

    public final MemberRefConstant memberAt(int index, String description) {
        try {
            return (MemberRefConstant) at(index);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, description, METHOD_REF, INTERFACE_METHOD_REF, FIELD_REF);
        }
    }

    public final MethodRefConstant methodAt(int index) {
        try {
            return (MethodRefConstant) at(index);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, null, METHOD_REF, INTERFACE_METHOD_REF);
        }
    }

    public final ClassMethodRefConstant classMethodAt(int index) {
        try {
            return (ClassMethodRefConstant) at(index);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, null, METHOD_REF);
        }
    }

    public final InterfaceMethodRefConstant interfaceMethodAt(int index) {
        try {
            return (InterfaceMethodRefConstant) at(index);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, null, INTERFACE_METHOD_REF);
        }
    }

    public final FieldRefConstant fieldAt(int index) {
        try {
            return (FieldRefConstant) at(index);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, null, FIELD_REF);
        }
    }

    public final StringConstant stringConstantAt(int index) {
        try {
            return (StringConstant) at(index);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, null, STRING);
        }
    }

    public final InvokeDynamicConstant indyAt(int index) {
        try {
            return (InvokeDynamicConstant) at(index);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, null, INVOKEDYNAMIC);
        }
    }

    // endregion unresolved constants

    @Override
    public String toString() {
        Formatter buf = new Formatter();
        for (int i = 0; i < length(); i++) {
            PoolConstant c = at(i);
            buf.format("#%d = %-15s // %s%n", i, c.tag(), c.toString(this));
        }
        return buf.toString();
    }

    /**
     * Creates a constant pool from a class file.
     */
    public static ConstantPool parse(EspressoLanguage language, ClassfileStream stream, ClassfileParser parser) {
        return parse(language, stream, parser, null, null);
    }

    /**
     * Creates a constant pool from a class file.
     */
    public static ConstantPool parse(EspressoLanguage language, ClassfileStream stream, ClassfileParser parser, StaticObject[] patches, EspressoContext context) {
        final int length = stream.readU2();
        if (length < 1) {
            throw stream.classFormatError("Invalid constant pool size (" + length + ")");
        }

        final PoolConstant[] entries = new PoolConstant[length];
        entries[0] = InvalidConstant.VALUE;

        int i = 1;
        while (i < length) {
            final int tagByte = stream.readU1();
            final Tag tag = Tag.fromValue(tagByte);
            if (tag == null) {
                throw new ClassFormatError("Invalid constant pool entry type at index " + i);
            }
            switch (tag) {
                case CLASS: {
                    if (existsAt(patches, i)) {
                        StaticObject classSpecifier = patches[i];
                        if (classSpecifier.getKlass().getType() == Symbol.Type.Class) {
                            entries[i] = new ClassConstant.PreResolved(((StaticObjectImpl) classSpecifier).getMirrorKlass());
                        } else {
                            entries[i] = new ClassConstant.WithString(context.getNames().lookup(Meta.toHostString(patches[i])));
                        }
                        stream.readU2();
                        break;
                    }
                    int classNameIndex = stream.readU2();
                    entries[i] = new ClassConstant.Index(classNameIndex);
                    break;
                }
                case STRING: {
                    if (existsAt(patches, i)) {
                        entries[i] = new StringConstant.PreResolved(patches[i]);
                        stream.readU2();
                    } else {
                        entries[i] = new StringConstant.Index(stream.readU2());
                    }
                    break;
                }
                case FIELD_REF: {
                    int classIndex = stream.readU2();
                    int nameAndTypeIndex = stream.readU2();
                    entries[i] = new FieldRefConstant.Indexes(classIndex, nameAndTypeIndex);
                    break;
                }
                case METHOD_REF: {
                    int classIndex = stream.readU2();
                    int nameAndTypeIndex = stream.readU2();
                    entries[i] = new ClassMethodRefConstant.Indexes(classIndex, nameAndTypeIndex);
                    break;
                }
                case INTERFACE_METHOD_REF: {
                    int classIndex = stream.readU2();
                    int nameAndTypeIndex = stream.readU2();
                    entries[i] = new InterfaceMethodRefConstant.Indexes(classIndex, nameAndTypeIndex);
                    break;
                }
                case NAME_AND_TYPE: {
                    int nameIndex = stream.readU2();
                    int typeIndex = stream.readU2();
                    entries[i] = new NameAndTypeConstant.Indexes(nameIndex, typeIndex);
                    break;
                }
                case INTEGER: {
                    if (existsAt(patches, i)) {
                        entries[i] = new IntegerConstant(context.getMeta().unboxInteger(patches[i]));
                        stream.readS4();
                        break;
                    }
                    entries[i] = new IntegerConstant(stream.readS4());
                    break;
                }
                case FLOAT: {
                    if (existsAt(patches, i)) {
                        entries[i] = new FloatConstant(context.getMeta().unboxFloat(patches[i]));
                        stream.readFloat();
                        break;
                    }
                    entries[i] = new FloatConstant(stream.readFloat());
                    break;
                }
                case LONG: {
                    if (existsAt(patches, i)) {
                        entries[i] = new LongConstant(context.getMeta().unboxLong(patches[i]));
                        stream.readS8();
                    } else {
                        entries[i] = new LongConstant(stream.readS8());
                    }
                    ++i;
                    try {
                        entries[i] = InvalidConstant.VALUE;
                    } catch (ArrayIndexOutOfBoundsException e) {
                        throw new ClassFormatError("Invalid long constant index " + (i - 1));
                    }
                    break;
                }
                case DOUBLE: {
                    if (existsAt(patches, i)) {
                        entries[i] = new DoubleConstant(context.getMeta().unboxDouble(patches[i]));
                        stream.readDouble();
                    } else {
                        entries[i] = new DoubleConstant(stream.readDouble());
                    }
                    ++i;
                    try {
                        entries[i] = InvalidConstant.VALUE;
                    } catch (ArrayIndexOutOfBoundsException e) {
                        throw new ClassFormatError("Invalid double constant index " + (i - 1));
                    }
                    break;
                }
                case UTF8: {
                    // Copy-less UTF8 constant.
                    // A new symbol is spawned (copy) only if doesn't already exists.
                    utf8EntryCount.inc();
                    entries[i] = language.getUtf8ConstantTable().getOrCreate(stream.readByteSequenceUTF());
                    break;
                }
                case METHODHANDLE: {
                    parser.checkInvokeDynamicSupport(tag);
                    int refKind = stream.readU1();
                    int refIndex = stream.readU2();
                    entries[i] = new MethodHandleConstant.Index(refKind, refIndex);
                    break;
                }
                case METHODTYPE: {
                    parser.checkInvokeDynamicSupport(tag);
                    int descriptorIndex = stream.readU2();
                    entries[i] = new MethodTypeConstant.Index(descriptorIndex);
                    break;
                }
                case DYNAMIC: {
                    parser.checkDynamicConstantSupport(tag);
                    int bootstrapMethodAttrIndex = stream.readU2();
                    int nameAndTypeIndex = stream.readU2();
                    entries[i] = new DynamicConstant.Indexes(bootstrapMethodAttrIndex, nameAndTypeIndex);
                    parser.updateMaxBootstrapMethodAttrIndex(bootstrapMethodAttrIndex);
                    break;
                }
                case INVOKEDYNAMIC: {
                    parser.checkInvokeDynamicSupport(tag);
                    int bootstrapMethodAttrIndex = stream.readU2();
                    int nameAndTypeIndex = stream.readU2();
                    entries[i] = new InvokeDynamicConstant.Indexes(bootstrapMethodAttrIndex, nameAndTypeIndex);
                    parser.updateMaxBootstrapMethodAttrIndex(bootstrapMethodAttrIndex);
                    break;
                }
                default: {
                    parser.handleBadConstant(tag, stream);
                    break;
                }
            }
            i++;
        }

        return new ConstantPoolImpl(entries);
    }

    private static boolean existsAt(Object[] patches, int index) {
        return patches != null && index <= patches.length && patches[index] != StaticObject.NULL;
    }
}
