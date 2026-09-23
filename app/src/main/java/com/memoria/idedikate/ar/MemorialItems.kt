package com.memoria.idedikate.ar

import android.content.Context
import android.util.Log
import com.memoria.idedikate.ads.RewardAdType
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.Color
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.ShapeFactory
import com.google.ar.sceneform.rendering.Texture
import java.util.concurrent.CompletableFuture

enum class MemorialItemType(val rewardAdType: RewardAdType) {
    PLAQUE(RewardAdType.REWARDED_DISPLAY),
    INCENSE_STICK(RewardAdType.REWARDED_INCENSE),
    INCENSE_POT(RewardAdType.REWARDED_INCENSE),
    INCENSE_BOX(RewardAdType.REWARDED_INCENSE),
    INCENSE_PAPER(RewardAdType.REWARDED_INCENSE),
    FRUIT_OFFERING(RewardAdType.REWARDED_FRUITS),
    FOOD_OFFERING(RewardAdType.REWARDED_FOOD),
    CANDLE(RewardAdType.REWARDED_DISPLAY),
    FLOWER(RewardAdType.REWARDED_DISPLAY)
}

data class Offset(
    val x: Float,
    val y: Float,
    val z: Float,
    val yaw: Float
)

object MemorialItems {

    fun renderOfferings(
        context: Context,
        anchorNode: Node,
        items: List<Pair<MemorialItemType, Offset>>,
        photoTexture: Texture? = null
    ) {
        items.forEach { (type, offset) ->
            val futureNode = when (type) {
                MemorialItemType.PLAQUE -> plaque(context, photoTexture)
                MemorialItemType.INCENSE_STICK -> incenseStick(context)
                MemorialItemType.INCENSE_POT -> incensePot(context)
                MemorialItemType.INCENSE_BOX -> incenseBox(context)
                MemorialItemType.INCENSE_PAPER -> incensePaper(context)
                MemorialItemType.FRUIT_OFFERING -> fruitOffering(context)
                MemorialItemType.FOOD_OFFERING -> foodOffering(context)
                MemorialItemType.CANDLE -> candle(context)
                MemorialItemType.FLOWER -> flower(context)
            }

            futureNode.thenAccept { node ->
                node.setParent(anchorNode)
                node.localPosition = Vector3(offset.x, offset.y, offset.z)
                node.localRotation = Quaternion.axisAngle(Vector3(0f, 1f, 0f), offset.yaw)
            }.exceptionally { throwable ->
                Log.e("MemorialItems", "Failed to render $type", throwable)
                null
            }
        }
    }

    /** Builds a single incense stick, ~25cm, origin at base centre. */
    fun incenseStick(context: Context): CompletableFuture<Node> {
        val tanColor = Color(0.55f, 0.35f, 0.18f)
        val brownColor = Color(0.38f, 0.20f, 0.10f)

        val tanFuture = MaterialFactory.makeOpaqueWithColor(context, tanColor)
        val brownFuture = MaterialFactory.makeOpaqueWithColor(context, brownColor)

        return tanFuture.thenCombine(brownFuture) { tan, brown ->
            val mainStickRadius = 0.0015f
            val mainStickHeight = 0.22f // top part
            val baseStickHeight = 0.03f // bottom part

            val stickNode = Node()
            
            val topRenderable = ShapeFactory.makeCylinder(
                mainStickRadius, 
                mainStickHeight, 
                Vector3(0f, mainStickHeight / 2f + baseStickHeight, 0f), 
                tan
            )
            val topNode = Node()
            topNode.renderable = topRenderable
            topNode.setParent(stickNode)

            val baseRenderable = ShapeFactory.makeCylinder(
                mainStickRadius, 
                baseStickHeight, 
                Vector3(0f, baseStickHeight / 2f, 0f), 
                brown
            )
            val baseNode = Node()
            baseNode.renderable = baseRenderable
            baseNode.setParent(stickNode)

            stickNode
        }
    }
    
    fun incensePot(context: Context): CompletableFuture<Node> { 
        val potColor = Color(0.8f, 0.7f, 0.3f) // Golden/brass color
        return MaterialFactory.makeOpaqueWithColor(context, potColor).thenApply { potMaterial ->
            val radius = 0.05f
            val height = 0.07f

            val potNode = Node()
            val potRenderable = ShapeFactory.makeCylinder(
                radius, 
                height, 
                Vector3(0f, height / 2f, 0f), 
                potMaterial
            )
            potNode.renderable = potRenderable
            potNode
        }
    }
    
    fun plaque(context: Context, photoTexture: Texture?): CompletableFuture<Node> { 
        val plaqueColor = Color(0.1f, 0.1f, 0.1f) // Dark granite
        return MaterialFactory.makeOpaqueWithColor(context, plaqueColor).thenCompose { plaqueMaterial ->
            val width = 0.22f
            val height = 0.28f
            val depth = 0.015f

            val plaqueNode = Node()
            val plaqueRenderable = ShapeFactory.makeCube(
                Vector3(width, height, depth), 
                Vector3(0f, height / 2f, 0f), 
                plaqueMaterial
            )
            
            plaqueNode.renderable = plaqueRenderable
            
            if (photoTexture != null) {
                MaterialFactory.makeOpaqueWithTexture(context, photoTexture).thenApply { photoMaterial ->
                    val photoNode = Node()
                    val photoWidth = width * 0.8f
                    val photoHeight = height * 0.8f
                    val photoDepth = 0.001f
                    
                    val photoRenderable = ShapeFactory.makeCube(
                        Vector3(photoWidth, photoHeight, photoDepth), 
                        Vector3(0f, height / 2f, depth / 2f + photoDepth / 2f), 
                        photoMaterial
                    )
                    photoNode.renderable = photoRenderable
                    photoNode.setParent(plaqueNode)
                    plaqueNode
                }
            } else {
                CompletableFuture.completedFuture(plaqueNode)
            }
        }
    }

    fun incenseBox(context: Context): CompletableFuture<Node> {
        val redColor = Color(0.7f, 0.1f, 0.1f)
        return MaterialFactory.makeOpaqueWithColor(context, redColor).thenApply { material ->
            val width = 0.08f
            val height = 0.04f
            val depth = 0.25f

            val node = Node()
            node.renderable = ShapeFactory.makeCube(
                Vector3(width, height, depth),
                Vector3(0f, height / 2f, 0f),
                material
            )
            node
        }
    }

    fun incensePaper(context: Context): CompletableFuture<Node> {
        val yellowColor = Color(0.9f, 0.8f, 0.2f)
        return MaterialFactory.makeOpaqueWithColor(context, yellowColor).thenApply { material ->
            val width = 0.15f
            val height = 0.002f
            val depth = 0.15f

            val node = Node()
            node.renderable = ShapeFactory.makeCube(
                Vector3(width, height, depth),
                Vector3(0f, height / 2f, 0f),
                material
            )
            node
        }
    }

    fun fruitOffering(context: Context): CompletableFuture<Node> {
        val orangeColor = Color(1.0f, 0.5f, 0.0f)
        return MaterialFactory.makeOpaqueWithColor(context, orangeColor).thenApply { material ->
            val radius = 0.04f

            val node = Node()
            
            // Central fruit
            val centralNode = Node()
            centralNode.renderable = ShapeFactory.makeSphere(
                radius,
                Vector3(0f, radius, 0f),
                material
            )
            centralNode.setParent(node)

            // Right fruit
            val rightNode = Node()
            rightNode.renderable = ShapeFactory.makeSphere(
                radius,
                Vector3(radius * 1.1f, radius, radius * 0.5f),
                material
            )
            rightNode.setParent(node)

            // Left fruit
            val leftNode = Node()
            leftNode.renderable = ShapeFactory.makeSphere(
                radius,
                Vector3(-radius * 1.1f, radius, radius * 0.5f),
                material
            )
            leftNode.setParent(node)

            node
        }
    }

    fun foodOffering(context: Context): CompletableFuture<Node> {
        val whiteColor = Color(0.9f, 0.9f, 0.9f)
        val brownColor = Color(0.6f, 0.3f, 0.1f)
        return MaterialFactory.makeOpaqueWithColor(context, whiteColor).thenCombine(
            MaterialFactory.makeOpaqueWithColor(context, brownColor)
        ) { bowlMat, foodMat ->
            val node = Node()
            
            val bowlRadius = 0.06f
            val bowlHeight = 0.04f
            val bowlNode = Node()
            bowlNode.renderable = ShapeFactory.makeCylinder(
                bowlRadius, bowlHeight, Vector3(0f, bowlHeight / 2f, 0f), bowlMat
            )
            bowlNode.setParent(node)

            val foodRadius = 0.05f
            val foodNode = Node()
            foodNode.renderable = ShapeFactory.makeSphere(
                foodRadius, Vector3(0f, bowlHeight + foodRadius / 2f, 0f), foodMat
            )
            foodNode.setParent(node)
            
            node
        }
    }

    fun candle(context: Context): CompletableFuture<Node> {
        val redColor = Color(0.8f, 0.1f, 0.1f)
        val flameColor = Color(1.0f, 0.9f, 0.0f)

        return MaterialFactory.makeOpaqueWithColor(context, redColor).thenCombine(
            MaterialFactory.makeOpaqueWithColor(context, flameColor)
        ) { candleMat, flameMat ->
            val node = Node()
            
            val radius = 0.015f
            val height = 0.15f
            val bodyNode = Node()
            bodyNode.renderable = ShapeFactory.makeCylinder(
                radius, height, Vector3(0f, height / 2f, 0f), candleMat
            )
            bodyNode.setParent(node)

            val flameRadius = 0.005f
            val flameNode = Node()
            flameNode.renderable = ShapeFactory.makeSphere(
                flameRadius, Vector3(0f, height + flameRadius, 0f), flameMat
            )
            flameNode.setParent(node)

            node
        }
    }

    fun flower(context: Context): CompletableFuture<Node> {
        val greenColor = Color(0.1f, 0.6f, 0.1f)
        val pinkColor = Color(1.0f, 0.4f, 0.7f)

        return MaterialFactory.makeOpaqueWithColor(context, greenColor).thenCombine(
            MaterialFactory.makeOpaqueWithColor(context, pinkColor)
        ) { stemMat, petalMat ->
            val node = Node()
            
            val stemRadius = 0.003f
            val stemHeight = 0.2f
            val stemNode = Node()
            stemNode.renderable = ShapeFactory.makeCylinder(
                stemRadius, stemHeight, Vector3(0f, stemHeight / 2f, 0f), stemMat
            )
            stemNode.setParent(node)

            val headRadius = 0.03f
            val headNode = Node()
            headNode.renderable = ShapeFactory.makeSphere(
                headRadius, Vector3(0f, stemHeight + headRadius, 0f), petalMat
            )
            headNode.setParent(node)

            node
        }
    }
}