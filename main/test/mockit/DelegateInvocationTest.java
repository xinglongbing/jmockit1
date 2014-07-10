/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit;

import java.util.concurrent.*;

import org.junit.*;
import static org.junit.Assert.*;

public final class DelegateInvocationTest
{
   static class Collaborator
   {
      Collaborator() {}
      Collaborator(@SuppressWarnings("unused") int i) {}

      int getValue() { return -1; }
      String doSomething(boolean b, int[] i, String s) { return s + b + i[0]; }
      static boolean staticMethod() { return true; }
      static boolean staticMethod(int i) { return i > 0; }
      native long nativeMethod(boolean b);
      private float privateMethod() { return 1.2F; }
   }

   @Test
   public void delegateWithContextObject(@Mocked Collaborator unused)
   {
      new NonStrictExpectations() {{
         Collaborator.staticMethod();
         result = new Delegate() {
            @Mock
            boolean staticMethod(Invocation context)
            {
               assertNull(context.getInvokedInstance());
               assertEquals(0, context.getInvokedArguments().length);
               assertEquals(context.getInvocationCount() - 1, context.getInvocationIndex());
               assertEquals(0, context.getMinInvocations());
               assertEquals(-1, context.getMaxInvocations());
               return context.getInvocationCount() > 0;
            }
         };
      }};

      assertTrue(Collaborator.staticMethod());
      assertTrue(Collaborator.staticMethod());
   }

   static class ConstructorDelegate implements Delegate<Void>
   {
      int capturedArgument;

      @Mock
      void init(Invocation context, int i)
      {
         assertNotNull(context.getInvokedInstance());
         capturedArgument = i + context.getInvocationCount();
      }
   }

   @Test
   public void delegateForConstructorWithContext(@Mocked Collaborator mock)
   {
      final ConstructorDelegate delegate = new ConstructorDelegate();

      new Expectations() {{
         new Collaborator(anyInt); result = delegate;
      }};

      new Collaborator(4);

      assertEquals(5, delegate.capturedArgument);
   }

   @Test
   public void delegateReceivingNullArguments(@Mocked final Collaborator mock)
   {
      new NonStrictExpectations() {
      {
         mock.doSomething(true, null, null);
         result = new Delegate() {
            @Mock
            void doSomething(Invocation invocation, Boolean b, int[] i, String s)
            {
               Collaborator instance = invocation.getInvokedInstance();
               assertSame(mock, instance);
               assertEquals(1, invocation.getInvocationCount());
               assertTrue(b);
               assertNull(i);
               assertNull(s);
               Object[] args = invocation.getInvokedArguments();
               assertEquals(3, args.length);
               assertTrue((Boolean) args[0]);
               assertNull(args[1]);
               assertNull(args[2]);
            }
         };
      }
      };

      assertNull(mock.doSomething(true, null, null));
   }

   @Test
   public void delegateWithAnotherMethodOnTheDelegateClass(@Mocked final Collaborator mock)
   {
      new NonStrictExpectations() {{
         mock.getValue();
         result = new Delegate() {
            @Mock
            int getValue(Invocation context)
            {
               return context.getInvocationCount();
            }

            @SuppressWarnings("unused")
            private void otherMethod(Invocation context)
            {
               fail();
            }
         };
      }};

      assertEquals(1, new Collaborator().getValue());
      assertEquals(2, new Collaborator().getValue());
   }

   @Test
   public void delegateClassWithMultipleMethodsAndInexactButValidMatch(@Mocked Collaborator mock)
   {
      new Expectations() {{
         Collaborator.staticMethod(1);
         result = new Delegate() {
            @SuppressWarnings("unused")
            private void otherMethod(int i)
            {
               fail();
            }

            @Mock
            boolean staticMethod(Invocation invocation, Number i)
            {
               assertEquals(1, invocation.getMinInvocations());
               assertEquals(1, invocation.getMaxInvocations());
               return i.intValue() > 0;
            }
         };
      }};

      assertTrue(Collaborator.staticMethod(1));
   }

   @Test
   public void delegateMethodWithNoParametersForExpectationWithParameters(@Mocked final Collaborator mock)
   {
      new Expectations() {{
         mock.nativeMethod(true);
         result = new Delegate() {
            @Mock
            long nonMatchingDelegate() { return 123L; }
         };
      }};

      assertEquals(123, mock.nativeMethod(true));
   }

   @Test(expected = IllegalArgumentException.class)
   public void delegateClassWithTwoPrivateMethods(@Mocked final Collaborator mock)
   {
      new NonStrictExpectations() {{
         mock.privateMethod();
         result = new Delegate() {
            @SuppressWarnings("unused")
            private float privateMethod(Invocation invocation) { return 1.0F; }

            @SuppressWarnings("unused")
            private void someOtherMethod() {}
         };
      }};
   }

   @Test
   public void delegateWithDifferentMethodName(@Mocked final Collaborator mock)
   {
      new NonStrictExpectations() {{
         mock.nativeMethod(anyBoolean);
         result = new Delegate() {
            @Mock
            long differentName(Invocation invocation, boolean b)
            {
               assertEquals(1, invocation.getInvocationCount());
               assertTrue(b);
               assertSame(Boolean.TRUE, invocation.getInvokedArguments()[0]);
               return 3L;
            }
         };
      }};

      assertEquals(3L, new Collaborator().nativeMethod(true));
   }

   @Test
   public void consecutiveDelegatesForTheSameExpectation(@Mocked final Collaborator mock)
   {
      new Expectations() {{
         mock.getValue();
         returns(
            new Delegate() {
               @Mock
               int delegate(Invocation invocation)
               {
                  assertSame(mock, invocation.getInvokedInstance());
                  assertEquals(1, invocation.getMinInvocations());
                  assertEquals(3, invocation.getMaxInvocations());
                  return invocation.getInvocationCount();
               }
            },
            new Delegate() {
               @Mock
               int delegate(Invocation invocation)
               {
                  return invocation.getInvocationCount();
               }
            },
            new Delegate() {
               @Mock
               int delegate(Invocation invocation)
               {
                  assertEquals(3, invocation.getInvocationCount());
                  throw new SecurityException();
               }
            });
      }};

      assertEquals(1, mock.getValue());
      assertEquals(2, mock.getValue());

      try {
         mock.getValue();
         fail();
      }
      catch (SecurityException ignore) {
         // OK
      }
   }

   @Test
   public void delegateMethodWithInvocationForInterface(@Mocked final Callable<String> mock) throws Exception
   {
      new NonStrictExpectations() {{
         mock.call();
         result = new Delegate() {
            @Mock String delegate(Invocation inv) { return inv.getInvokedMember().getDeclaringClass().getName(); }
         };
      }};

      String s = mock.call();

      assertEquals(Callable.class.getName(), s);
   }

   @SuppressWarnings("deprecation")
   @Test
   public void useOfContextParametersForJREMethods(@Mocked({"runFinalizersOnExit", "exec"}) final Runtime rt)
      throws Exception
   {
      new NonStrictExpectations() {{
         Runtime.runFinalizersOnExit(anyBoolean); minTimes = 1;
         result = new Delegate() {
            @Mock
            void delegate(Invocation inv, boolean b)
            {
               assertNull(inv.getInvokedInstance());
               assertEquals(1, inv.getInvocationCount());
               assertEquals(1, inv.getMinInvocations());
               assertEquals(-1, inv.getMaxInvocations());
               assertTrue(b);
            }
         };

         rt.exec(anyString, null); maxTimes = 1;
         result = new Delegate() {
            @Mock
            void exec(Invocation inv, String command, String[] envp)
            {
               assertSame(Runtime.getRuntime(), inv.getInvokedInstance());
               assertEquals(0, inv.getInvocationIndex());
               assertEquals(0, inv.getMinInvocations());
               assertEquals(1, inv.getMaxInvocations());
               assertNotNull(command);
               assertNull(envp);
            }
         };
      }};

      Runtime.runFinalizersOnExit(true);
      assertNull(Runtime.getRuntime().exec("test", null));
   }
}