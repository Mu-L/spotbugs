/*
 * FindBugs - Find bugs in Java programs
 * Copyright (C) 2005 Dave Brosius <dbrosius@users.sourceforge.net>
 * Copyright (C) 2005 University of Maryland
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package edu.umd.cs.findbugs.detect;

import java.util.Collections;
import java.util.Set;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantDouble;
import org.apache.bcel.classfile.ConstantFloat;
import org.apache.bcel.classfile.ConstantLong;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.StatelessDetector;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * Find occurrences of Math using constants, where the result of the calculation
 * can be determined statically. Replacing the math formula with the constant
 * performs better, and sometimes is more accurate.
 *
 * @author Dave Brosius
 */
public class UnnecessaryMath extends BytecodeScanningDetector implements StatelessDetector {
    static final int SEEN_NOTHING = 0;

    static final int SEEN_DCONST = 1;

    private final BugReporter bugReporter;

    private int state = SEEN_NOTHING;

    private double constValue;

    @edu.umd.cs.findbugs.internalAnnotations.StaticConstant
    private static final Set<String> zeroMethods = Set.of("acos", "asin", "atan", "atan2", "cbrt", "cos", "cosh", "exp", "expm1", "log", "log10",
            "pow", "sin", "sinh", "sqrt", "tan", "tanh", "toDegrees", "toRadians");

    @edu.umd.cs.findbugs.internalAnnotations.StaticConstant
    private static final Set<String> oneMethods = Set.of("acos", "asin", "atan", "cbrt", "exp", "log", "log10", "pow", "sqrt", "toDegrees");

    @edu.umd.cs.findbugs.internalAnnotations.StaticConstant
    private static final Set<String> anyMethods = Set.of("abs", "ceil", "floor", "rint", "round");

    public UnnecessaryMath(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        if (hasInterestingClass(classContext.getJavaClass().getConstantPool(), Collections.singleton("java/lang/Math"))) {
            super.visitClassContext(classContext);
        }
    }

    @Override
    public void visit(Code obj) {
        // Don't complain about unnecessary math calls in class initializers,
        // since they may be there to improve readability.
        if (Const.STATIC_INITIALIZER_NAME.equals(getMethod().getName())) {
            return;
        }

        state = SEEN_NOTHING;
        super.visit(obj);
    }

    @Override
    public void sawOpcode(int seen) {
        if (state == SEEN_NOTHING) {
            if ((seen == Const.DCONST_0) || (seen == Const.DCONST_1)) {
                constValue = seen - Const.DCONST_0;
                state = SEEN_DCONST;
            } else if ((seen == Const.LDC2_W) || (seen == Const.LDC_W)) {
                state = SEEN_DCONST;
                Constant c = this.getConstantRefOperand();
                if (c instanceof ConstantDouble) {
                    constValue = ((ConstantDouble) c).getBytes();
                } else if (c instanceof ConstantFloat) {
                    constValue = ((ConstantFloat) c).getBytes();
                } else if (c instanceof ConstantLong) {
                    constValue = ((ConstantLong) c).getBytes();
                } else {
                    state = SEEN_NOTHING;
                }
            }
        } else if (state == SEEN_DCONST) {
            if (seen == Const.INVOKESTATIC) {
                state = SEEN_NOTHING;
                if ("java.lang.Math".equals(getDottedClassConstantOperand())) {
                    String methodName = getNameConstantOperand();

                    if (((constValue == 0.0) && zeroMethods.contains(methodName))
                            || ((constValue == 1.0) && oneMethods.contains(methodName)) || (anyMethods.contains(methodName))) {
                        bugReporter.reportBug(new BugInstance(this, "UM_UNNECESSARY_MATH", LOW_PRIORITY).addClassAndMethod(this)
                                .addSourceLine(this));
                    }
                }
            }
            state = SEEN_NOTHING;
        }
    }
}
