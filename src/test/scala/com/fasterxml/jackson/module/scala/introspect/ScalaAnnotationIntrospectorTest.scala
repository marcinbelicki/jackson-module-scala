package com.fasterxml.jackson
package module.scala
package introspect

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.ContextualSerializer
import com.fasterxml.jackson.databind.{BeanDescription, DeserializationFeature, JsonSerializer, MapperFeature, ObjectMapper, SerializerProvider}
import org.scalatest.LoneElement.convertToCollectionLoneElementWrapper
import org.scalatest.Outcome
import org.scalatest.flatspec.FixtureAnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.beans.BeanProperty
import scala.collection.JavaConverters._

object ScalaAnnotationIntrospectorTest {
  class Token
  class BasicPropertyClass(val param: Int)
  class BeanPropertyClass(@BeanProperty val param: Int)
  class AnnotatedBasicPropertyClass(@JsonScalaTestAnnotation val param: Token)
  class AnnotatedBeanPropertyClass(@JsonScalaTestAnnotation @BeanProperty val param: Token)
  class JavaBeanPropertyClass {
    private var value: Int = 0
    @JsonProperty def getValue: Int = value
    @JsonProperty def setValue(value: Int): Unit = { this.value = value }
  }

  case class CaseClassWithDefault(a: String = "defaultParam", b: Option[String] = Some("optionDefault"), c: Option[String])
}

class ScalaAnnotationIntrospectorTest extends FixtureAnyFlatSpec with Matchers {
  import ScalaAnnotationIntrospectorTest._

  type FixtureParam = ObjectMapper

  override def withFixture(test: OneArgTest): Outcome = {
    val mapper = new ObjectMapper
    mapper.registerModule(DefaultScalaModule)
    withFixture(test.toNoArgTest(mapper))
  }

  behavior of "ScalaAnnotationIntrospector"

  it should "detect a val property" in { mapper =>
    val bean = new BasicPropertyClass(1)
    val allProps = getProps(mapper, bean)
    allProps.loneElement should have (Symbol("name") ("param"))

    val prop = allProps.asScala.head
    prop should have (
      Symbol("hasField") (true),
      Symbol("hasGetter") (true),
      Symbol("hasConstructorParameter") (true)
    )

    val accessor = prop.getAccessor
    accessor shouldNot be (null)
  }

  it should "detect annotations on a val property" in { mapper =>
    mapper.registerModule(new SimpleModule() {
      addSerializer(new JsonSerializer[Token] with ContextualSerializer {
        override val handledType: Class[Token] = classOf[Token]
        override def serialize(value: Token, gen: JsonGenerator, serializers: SerializerProvider): Unit =
          gen.writeString("")
        override def createContextual(prov: SerializerProvider, property: databind.BeanProperty): JsonSerializer[_] = {
          property.getAnnotation(classOf[JsonScalaTestAnnotation]) shouldNot be (null)
          this
        }
      })
    })

    val bean = new AnnotatedBasicPropertyClass(new Token)
    mapper.writeValueAsString(bean)
  }

  it should "detect a bean property" in { mapper =>
    val bean = new BeanPropertyClass(1)
    val allProps = getProps(mapper, bean)
    allProps.loneElement should have (Symbol("name") ("param"))

    val prop = allProps.asScala.head
    prop should have (
      Symbol("hasField") (true),
      Symbol("hasGetter") (true),
      Symbol("hasConstructorParameter") (true)
    )

    val accessor = prop.getAccessor
    accessor shouldNot be (null)
  }

  it should "detect annotations on a bean property" in { mapper =>
    mapper.registerModule(new SimpleModule() {
      addSerializer(new JsonSerializer[Token] with ContextualSerializer {
        override val handledType: Class[Token] = classOf[Token]
        override def serialize(value: Token, gen: JsonGenerator, serializers: SerializerProvider): Unit =
          gen.writeString("")
        override def createContextual(prov: SerializerProvider, property: databind.BeanProperty): JsonSerializer[_] = {
          property.getAnnotation(classOf[JsonScalaTestAnnotation]) shouldNot be (null)
          this
        }
      })
    })

    val bean = new AnnotatedBeanPropertyClass(new Token)
    val allProps = getProps(mapper, bean)
    allProps.loneElement should have (Symbol("name") ("param"))

    val prop = allProps.asScala.head
    prop should have (
      Symbol("hasField") (true),
      Symbol("hasGetter") (true),
      Symbol("hasConstructorParameter") (true)
    )
    val param = prop.getConstructorParameter
    param.getAnnotation(classOf[JsonScalaTestAnnotation]) shouldNot be (null)

    mapper.writeValueAsString(bean)
  }

  it should "correctly infer name(s) of un-named bean properties" in { mapper =>
    val tree = mapper.valueToTree[databind.node.ObjectNode](new JavaBeanPropertyClass)
    tree.has("value") shouldBe true
    tree.has("setValue") shouldBe false
    tree.has("getValue") shouldBe false
  }

  it should "respect APPLY_DEFAULT_VALUES true" in { _ =>

    val builder = JsonMapper.builder().enable(MapperFeature.APPLY_DEFAULT_VALUES).addModule(DefaultScalaModule)
    val mapper = builder.build()

    val json = """
        |{}
        |""".stripMargin

    val jsonWithKey = """
                 |{"a": "notDefault"}
                 |""".stripMargin

    val jsonWithNulls = """
                          |{"a": null, "b": null, "c": null}
                          |""".stripMargin

    val withDefault = mapper.readValue(json, classOf[CaseClassWithDefault])
    val withoutDefault = mapper.readValue(jsonWithKey, classOf[CaseClassWithDefault])
    val withNulls = mapper.readValue(jsonWithNulls, classOf[CaseClassWithDefault])


    withDefault.a shouldBe "defaultParam"

    withoutDefault.a shouldBe "notDefault"

    withNulls.a shouldBe "defaultParam"
    withNulls.b shouldBe Some("optionDefault")
    withNulls.c shouldBe None
  }

  it should "respect APPLY_DEFAULT_VALUES false" in { _ =>
    val builder = JsonMapper.builder().disable(MapperFeature.APPLY_DEFAULT_VALUES).addModule(DefaultScalaModule)
    val mapper = builder.build()

    val json = """
                 |{}
                 |""".stripMargin

    val jsonWithKey = """
                        |{"a": "notDefault"}
                        |""".stripMargin

    val jsonWithNulls = """
                        |{"a": null, "b": null, "c": null}
                        |""".stripMargin

    val withDefault = mapper.readValue(json, classOf[CaseClassWithDefault])
    val withoutDefault = mapper.readValue(jsonWithKey, classOf[CaseClassWithDefault])
    val withNulls = mapper.readValue(jsonWithNulls, classOf[CaseClassWithDefault])

    withDefault.a shouldBe null

    withoutDefault.a shouldBe "notDefault"

    withNulls.a shouldBe null
    withNulls.b shouldBe None
    withNulls.c shouldBe None
  }

  private def getProps(mapper: ObjectMapper, bean: AnyRef) = {
    val config = mapper.getSerializationConfig
    val beanDescription: BeanDescription = config.introspect(mapper.constructType(bean.getClass))
    beanDescription.findProperties()
  }
}
