// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at http://mozilla.org/MPL/2.0/.
//
// Copyright (c) 2011-2022 ETH Zurich.

package viper.silver.plugin.standard.adt

import viper.silver.ast.{Member, NoInfo, NoPosition, Position, Type, TypeVar}
import viper.silver.parser.Transformer.ParseTreeDuplicationError
import viper.silver.parser.{NameAnalyser, PAnyFormalArgDecl, PExtender, PGenericType, PGlobalDeclaration, PIdentifier, PIdnDef, PIdnUse, PMember, PNode, PType, PTypeSubstitution, PTypeVarDecl, Translator, TypeChecker}
import viper.silver.plugin.standard.adt.PAdtConstructor.findAdtConstructor

import scala.reflect.runtime.{universe => reflection}

case class PAdt(idndef: PIdnDef, typVars: Seq[PTypeVarDecl], constructors: Seq[PAdtConstructor])(val pos: (Position, Position)) extends PExtender with PMember with PGlobalDeclaration {

  override val getSubnodes: Seq[PNode] = Seq(idndef) ++ typVars ++ constructors

  override def typecheck(t: TypeChecker, n: NameAnalyser): Option[Seq[String]] = {
    t.checkMember(this) {
      this.constructors foreach (_.typecheck(t, n))
    }
    None
  }

  override def translateMemberSignature(t: Translator): Adt = {
    Adt(idndef.name, null, typVars map (t => TypeVar(t.idndef.name)))(t.liftPos(this))
  }

  override def translateMember(t: Translator): Member = {

    // In a first step translate constructor signatures
    constructors map ( c => {
      val cc = c.translateMemberSignature(t)
      t.getMembers().put(c.idndef.name, cc)
    })

    val a = PAdt.findAdt(idndef, t)
    val aa = a.copy(constructors = constructors map (_.translateMember(t)))(a.pos, a.info, a.errT)
    t.getMembers()(a.name) = aa
    aa
  }

}

object PAdt {
  /**
    * This is a helper method helper that can be called if one knows which 'id' refers to an ADT
    *
    * @param id identifier of the ADT
    * @param t translator unit
    * @return the corresponding ADT object
    */
  def findAdt(id: PIdentifier, t: Translator): Adt = t.getMembers()(id.name).asInstanceOf[Adt]

}

case class PAdtConstructor(idndef: PIdnDef, formalArgs: Seq[PAnyFormalArgDecl])(val adtName: PIdnUse)(val pos: (Position, Position)) extends PExtender with PMember with PGlobalDeclaration {

  override val getSubnodes: Seq[PNode] = Seq(idndef) ++ formalArgs

  override def typecheck(t: TypeChecker, n: NameAnalyser): Option[Seq[String]] = {
    this.formalArgs foreach (a => t.check(a.typ))
    None
  }

  override def translateMemberSignature(t: Translator): AdtConstructor = {
    AdtConstructor(idndef.name, formalArgs map t.liftAnyVarDecl)(t.liftPos(this), NoInfo, adtName.name)
  }

  override def translateMember(t: Translator): AdtConstructor = {
    findAdtConstructor(idndef, t)
  }

  override def withChildren(children: Seq[Any], pos: Option[(Position, Position)] = None, forceRewrite: Boolean = false): this.type = {
    if (!forceRewrite && this.children == children && pos.isEmpty)
      this
    else {

      // TODO: Why can we not simplify with following code? => results in an Exception, is reflection really the only way?
      /*
      val first = children.head.asInstanceOf[PIdnDef]
      val others = children.tail.asInstanceOf[Seq[PAnyFormalArgDecl]]


      PAdtConstructor(first, others)(this.adtName)(pos.getOrElse(this.pos)).asInstanceOf[this.type]
      */

      // Infer constructor from type
      val mirror = reflection.runtimeMirror(reflection.getClass.getClassLoader)
      val instanceMirror = mirror.reflect(this)
      val classSymbol = instanceMirror.symbol
      val classMirror = mirror.reflectClass(classSymbol)
      val constructorSymbol = instanceMirror.symbol.primaryConstructor.asMethod
      val constructorMirror = classMirror.reflectConstructor(constructorSymbol)

      // Add additional arguments to constructor call, besides children
      val firstArgList = children
      var secondArgList = Seq.empty[Any]

      this match {
        case pd: PAdtConstructor => secondArgList = Seq(pd.adtName) ++ Seq(pos.getOrElse(pd.pos))
        case _ =>
      }

      // Call constructor
      val newNode = try {
        constructorMirror(firstArgList ++ secondArgList: _*)
      }
      catch {
        case _: Exception if this.isInstanceOf[PNode] =>
          throw ParseTreeDuplicationError(this.asInstanceOf[PNode], children)
      }

      newNode.asInstanceOf[this.type]
    }
  }
}

object PAdtConstructor {
  /**
    * This is a helper method helper that can be called if one knows which 'id' refers to an ADTConstructor
    *
    * @param id identifier of the ADT constructor
    * @param t  translator unit
    * @return the corresponding ADTConstructor object
    */
  def findAdtConstructor(id: PIdentifier, t: Translator): AdtConstructor = t.getMembers()(id.name).asInstanceOf[AdtConstructor]
}

case class PAdtConstructor1(idndef: PIdnDef, formalArgs: Seq[PAnyFormalArgDecl])(val pos: (Position, Position))

case class PAdtType(adt: PIdnUse, args: Seq[PType])(val pos: (Position, Position)) extends PExtender with PGenericType {

  var kind: PAdtTypeKinds.Kind = PAdtTypeKinds.Unresolved

  override def genericName: String = adt.name

  override def typeArguments: Seq[PType] = args

  override def isValidOrUndeclared: Boolean = (kind==PAdtTypeKinds.Adt || isUndeclared) && args.forall(_.isValidOrUndeclared)

  override def substitute(ts: PTypeSubstitution): PType = {
    require(kind==PAdtTypeKinds.Adt || isUndeclared)

    val newArgs = args map (a=>a.substitute(ts))
    if (args==newArgs)
      return this

    val newAdtType = PAdtType(adt,newArgs)((NoPosition, NoPosition))
    newAdtType.kind = PAdtTypeKinds.Adt
    newAdtType
  }

  override def getSubnodes(): Seq[PNode] = Seq(adt) ++ args

  def isResolved: Boolean = kind != PAdtTypeKinds.Unresolved

  def isUndeclared: Boolean = kind == PAdtTypeKinds.Undeclared

  override def subNodes: Seq[PType] = args

  override def typecheck(t: TypeChecker, n: NameAnalyser): Option[Seq[String]] = {
      this match {
        case at@PAdtType(_, _) if at.isResolved => None
        /* Already resolved, nothing left to do */
        case at@PAdtType(adt, args) =>
          assert(!at.isResolved, "Only yet-unresolved adt types should be type-checked and resolved")

          args foreach t.check

          var x: Any = null

          try {
            x = t.names.definition(t.curMember)(adt)
          } catch {
            case _: Throwable =>
          }

          x match {
            case PAdt(_, typVars, _) =>
              t.ensure(args.length == typVars.length, this, "wrong number of type arguments")
              at.kind = PAdtTypeKinds.Adt
              None
            case _ =>
              at.kind = PAdtTypeKinds.Undeclared
              Option(Seq(s"found undeclared type ${at.adt.name}"))
          }
      }
  }

  override def translateType(t: Translator): Type = {
    t.getMembers().get(adt.name) match {
      case Some(d) =>
        val adt = d.asInstanceOf[Adt]
        val typVarMapping = adt.typVars zip (args map t.ttyp)
        AdtType(adt, typVarMapping.toMap)
      case None => sys.error("undeclared adt type")
    }
  }
}

object PAdtTypeKinds {
  trait Kind
  case object Unresolved extends Kind
  case object Adt extends Kind
  case object Undeclared extends Kind
}