/*
 * The MIT License
 *
 * Copyright 2018 jakob.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.company.cica;

import java.util.ArrayList;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 *
 * @author jakob
 */
public class Inputs {
    public static Input fromProvider(Supplier<Object> supplier, Predicate<Object> marksEnd) {
        return new Input() {
            private boolean shouldTake = true;
            private Object current;
            private int index;
            private ArrayList<Object> buffer = new ArrayList<>();
            
            @Override
            public Object take() {
                ensureCurrentSet();
                
                shouldTake = true;
                return current;
            }

            @Override
            public Object peek() {
                ensureCurrentSet();
                
                return current;
            }

            @Override
            public boolean atEnd() {
                ensureCurrentSet();
                
                return marksEnd.test(current);
            }
            
            private void ensureCurrentSet() {
                if(shouldTake) {
                    current = supplier.get();
                    shouldTake = false;
                }
            }

            @Override
            public InputState getState() {
                throw new UnsupportedOperationException("Input is not recoverable.");
            }
        };
    }
    
    public static Input toRecoverable(Input input) {
        return new Input() {
            private ArrayList<Object> buffer = new ArrayList<>();
            private int index;

            @Override
            public InputState getState() {
                int savedIndex = index;
                
                return () -> this.index = savedIndex;
            }

            @Override
            public Object take() {
                ensureBuffered(index);
                Object o = buffer.get(index);
                index++;
                return o;
            }
            
            private void ensureBuffered(int index) {
                while(buffer.size() <= index) {
                    if(!input.atEnd()) {
                        buffer.add(input.take());
                    } else {
                        return;
                    }
                }
            }

            @Override
            public Object peek() {
                return buffer.get(index);
            }

            @Override
            public boolean atEnd() {
                ensureBuffered(index);
                return index >= buffer.size();
            }
        };
    }
}
